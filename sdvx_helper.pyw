from decimal import Decimal
from functools import partial
import json, datetime, winsound
import logging, logging.handlers
import os, sys, re
import subprocess
import threading
import time
from tkinter import filedialog
import traceback
import urllib
import webbrowser

from PIL import Image, ImageFilter
from bs4 import BeautifulSoup
import imagehash, keyboard
import requests

import PySimpleGUI as sg
from gen_summary import *
from manage_settings import *
import numpy as np
from obssocket import OBSSocket
from poor_man_resource_bundle import *
import pyautogui as pgui
import sdvx_utils
from sdvxh_classes import *

from discord_presence import SDVX_discord_presence


# フラットウィンドウ、右下モード(左に上部側がくる)
# フルスクリーン、2560x1440に指定してもキャプは1920x1080で撮れてるっぽい
os.makedirs('jackets', exist_ok=True)
os.makedirs('log', exist_ok=True)
os.makedirs('out', exist_ok=True)
logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)
hdl = logging.handlers.RotatingFileHandler(
    f'log/{os.path.basename(__file__).split(".")[0]}.log',
    encoding='utf-8',
    maxBytes=1024*1024*2,
    backupCount=1,
)
hdl.setLevel(logging.DEBUG)
hdl_formatter = logging.Formatter('%(asctime)s %(filename)s:%(lineno)5d %(funcName)s() [%(levelname)s] %(message)s')
hdl.setFormatter(hdl_formatter)
logger.addHandler(hdl)

### 固定値
FONT = ('Meiryo',12)
FONTs = ('Meiryo',8)
par_text = partial(sg.Text, font=FONT)
par_btn = partial(sg.Button, pad=(3,0), font=FONT, enable_events=True, border_width=0)
SETTING_FILE = 'settings.json'
sg.theme('SystemDefault')

SWVER = sdvx_utils.get_version("helper")
    
class SDVXHelper:
    def __init__(self):
        # TODO: Save default locale to the setting.json and loaded it here
        self.default_locale = 'EN'
        self.bundle = PoorManResourceBundle(self.default_locale)
        self.i18n = self.bundle.get_text
        self.ico=self.ico_path('icon.ico')
        self.detect_mode = detect_mode.init
        self.gui_mode    = gui_mode.init
        self.last_play0_time = datetime.datetime.now()
        self.last_play1_time = datetime.datetime.now()
        self.last_autosave_time = datetime.datetime.now()
        self.img_rot = False # 正しい向きに直したImage形式の画像
        self.stop_thread = False # 強制停止用
        self.is_blastermax = False
        self.gen_first_vf = False
        self.window = False
        self.obs = False
        # RTA関連
        self.rta_mode = False
        self.rta_finished = False
        self.rta_starttime = datetime.datetime.now()
        self.rta_endtime = datetime.datetime.now()
        self.rta_target_vf = Decimal('20.0')

        self.plays = 0
        self.playtime = datetime.timedelta(seconds=0) # 楽曲プレイ時間の合計
        self.imgpath = os.getcwd()+'/out/capture.png'

        keyboard.add_hotkey('F4', self.capture_volforce_btn)
        keyboard.add_hotkey('F5', self.capture_summary_btn)
        keyboard.add_hotkey('F6', self.save_screenshot_general)
        keyboard.add_hotkey('F7', self.import_score_on_select_with_dialog)
        keyboard.add_hotkey('F8', self.update_rival)
        keyboard.add_hotkey('F9', self.start_rta_mode)

        self.load_settings()
        self.save_settings() # 値が追加された場合のために、一度保存
        self.update_musiclist()
        self.sdvx_logger = SDVXLogger(player_name=self.settings['player_name'])
        self.sdvx_logger.gen_sdvx_battle(False)
        self.vf_pre = self.sdvx_logger.total_vf # アプリ起動時のVF
        self.vf_cur = self.sdvx_logger.total_vf # 最新のVF
        self.connect_obs()
        vf_str = f"{self.settings['obs_txt_vf_header']}{self.vf_cur:.3f} ({self.vf_cur-self.vf_pre:+.3f}){self.settings['obs_txt_vf_footer']}"
        if self.obs != False:
            self.obs.change_text(self.settings['obs_txt_vf_with_diff'], vf_str)

        self.gen_summary = False
        #self.result_vf_saved = False
        self.last_vf_hash = None
        
        self.session_plays = []
        
        logger.debug('created.')
        logger.debug(f'settings:{self.settings}')
        
    def setup_discord_presence(self):
        self.presence = SDVX_discord_presence()
        self.presence.update_custom(custom_text="Starting...")
        self.logToWindow("Discord presence initilized")
        
        
    def logToWindow(self, msg):
        if self.window and not self.window.is_closed()  :
            self.window['output'].print(msg)
        else :
            print(msg)
    

    def ico_path(self, relative_path:str):
        """アイコン表示用

        Args:
            relative_path (str): アイコンファイル名

        Returns:
            str: アイコンファイルの絶対パス
        """
        try:
            base_path = sys._MEIPASS
        except Exception:
            base_path = os.path.abspath(".")
        return os.path.join(base_path, relative_path)
    
    def update_musiclist(self):
        """曲リスト(musiclist.pkl)を最新化する
        """
        try:
            if self.settings['autoload_musiclist']:
                with urllib.request.urlopen(self.params['url_musiclist']) as wf:
                    with open('resources/musiclist.pkl', 'wb') as f:
                        f.write(wf.read())
                self.logToWindow(self.i18n('message.music.list.updated'))
        except Exception:
            print(traceback.format_exc())

    
    def load_settings(self):
        """ユーザ設定(self.settings)をロードしてself.settingsにセットする。一応返り値にもする。

        Returns:
            dict: ユーザ設定
        """
        ret = {}
        try:
            with open(SETTING_FILE) as f:
                ret = json.load(f)
                self.logToWindow(self.i18n('message.settings.loaded'))
        except Exception as e:
            logger.debug(traceback.format_exc())
            self.logToWindow(self.i18n('message.settings.not.found'))

        ### 後から追加した値がない場合にもここでケア
        for k in default_val.keys():
            if not k in ret.keys():
                self.logToWindow(f"{k} {self.i18n('message.settings.key.not.found')} ({default_val[k]} {self.i18n('message.settings.key.not.found.used')})")
                ret[k] = default_val[k]
        self.settings = ret
        self.check_legacy_settings()
        with open(self.settings['params_json'], 'r') as f:
            self.params = json.load(f)
        return ret

    def save_settings(self):
        """ユーザ設定(self.settings)を保存する。
        """
        with open(SETTING_FILE, 'w') as f:
            json.dump(self.settings, f, indent=2)

    def check_legacy_settings(self):
        """古くなった設定からの移行時に呼び出す関数。古いパラメータがある際に一度だけ呼び出す想定。
        """
        if len(self.settings['webhook_enable_lamps']) == 5: # maxxive追加前
            self.settings['webhook_enable_lamps'].insert(2, False)
        if 'top_is_right' in self.settings.keys(): # 1.0.29, 画面回転モードの判定
            if self.settings['top_is_right']:
                self.settings['orientation_top'] = 'right'
            else:
                self.settings['orientation_top'] = 'left'
            self.settings.pop('top_is_right')
            self.logToWindow('old parameter is updated.\n(top_is_right -> orientation_top)')

    def save_screenshot_general(self):
        """ゲーム画面のスクショを保存する。ホットキーで呼び出す用。
        """
        title = False
        now = datetime.datetime.now()
        self.last_autosave_time = now
        fmtnow = format(now, "%Y%m%d_%H%M%S")
        dst = f"{self.settings['autosave_dir']}/sdvx_{fmtnow}.png"
        dst_filename = f"sdvx_{fmtnow}.png"
        tmp = self.get_capture_after_rotate()
        self.gen_summary.cut_result_parts(tmp)
        cur,pre = self.gen_summary.get_score(tmp)
        res_ocr = self.gen_summary.ocr(notify=True)
        if res_ocr != False and self.detect_mode == detect_mode.result: # OCRで曲名認識に成功
            title = res_ocr
            for ch in ('\\', '/', ':', '*', '?', '"', '<', '>', '|'):
                title = title.replace(ch, '')
            # The OCR reader doesn't handle well spaces in the file name and will re-add them, so might as well add them here    
            for ch in (' ', '　'):
                title = title.replace(ch, '_')
            dst = f"{self.settings['autosave_dir']}/sdvx_{title[:120]}_{self.gen_summary.difficulty.upper()}_{self.gen_summary.lamp}_{str(cur)[:-4]}_{fmtnow}.png"
            dst_filename = f"sdvx_{title[:120]}_{self.gen_summary.difficulty.upper()}_{self.gen_summary.lamp}_{str(cur)[:-4]}_{fmtnow}.png"
        tmp.save(dst)
        lamp = ''
        difficulty = ''
        try:
            lamp = self.gen_summary.lamp
            difficulty = self.gen_summary.difficulty
        except:
            #print(traceback.format_exc())
            pass
        tmp_playdata = OnePlayData(title='???', cur_score=cur, pre_score=pre, lamp=lamp, difficulty=difficulty, date=fmtnow)
        if res_ocr != False: # OCR通過時、ファイルのタイムスタンプを使うためにここで作成
            ts = os.path.getmtime(dst)
            now = datetime.datetime.fromtimestamp(ts)
            tmp_playdata = self.sdvx_logger.push(res_ocr, cur, pre, self.gen_summary.lamp, self.gen_summary.difficulty, fmtnow)
            # RTA用
            if self.rta_mode:
                self.rta_logger.push(res_ocr, cur, pre, self.gen_summary.lamp, self.gen_summary.difficulty, fmtnow)
                self.rta_vf_cur = self.rta_logger.total_vf
                if Decimal(str(self.rta_vf_cur))>=Decimal(self.settings['rta_target_vf']):
                    self.rta_finished = True
                    current = self.rta_endtime if self.rta_finished else datetime.datetime.now()
                    self.rta_endtime = datetime.datetime.now()
                    rta_time = (self.rta_endtime - self.rta_starttime)
                    self.obs.change_text('sdvx_helper_rta_timer', str(rta_time).split('.')[0])
                    self.logToWindow(f"Timer stop! ({str(rta_time).split('.')[0]}), vf:{self.rta_vf_cur}")
                    self.rta_logger.rta_timer = str(rta_time).split('.')[0]
                    self.rta_logger.update_stats()
                rta_vf_str = f"{self.settings['obs_txt_vf_header']}{self.rta_vf_cur:.3f}{self.settings['obs_txt_vf_footer']}"
                self.obs.change_text('sdvx_helper_rta_vf', rta_vf_str)
                tmp_playdata.disp()
            self.vf_cur = self.sdvx_logger.total_vf # アプリ起動時のVF
            vf_str = f"{self.settings['obs_txt_vf_header']}{self.vf_cur:.3f} ({self.vf_cur-self.vf_pre:+.3f}){self.settings['obs_txt_vf_footer']}"
            self.obs.change_text(self.settings['obs_txt_vf_with_diff'], vf_str)
        self.th_webhook = threading.Thread(target=self.send_custom_webhook, args=(tmp_playdata,), daemon=True)
        self.th_webhook.start()
            
        self.gen_summary.generate() # ここでサマリも更新
        self.logToWindow(f"{self.i18n('message.screenshot.saved')} -> {dst_filename}")

        # ライバル欄更新
        if type(title) == str:
            self.sdvx_logger.update_rival_view(title, self.gen_summary.difficulty.upper())
        
        self.update_mybest()
        self.add_result_to_playlist(tmp_playdata)

    def update_mybest(self):
        """自己べ情報をcsv出力する
        """
        try:
            if self.settings['my_googledrive'] != '':
                self.sdvx_logger.gen_best_csv(self.settings['my_googledrive']+'/sdvx_helper_best.csv')
        except Exception:
            logger.debug(traceback.format_exc())

    def load_rivallog(self):
        """前回起動時に保存していたライバルの自己べ情報を読み込む
        """
        try:
            with open('out/rival_log.pkl', 'rb') as f:
                self.rival_log = pickle.load(f)
        except:
            self.rival_log = {}
        logger.debug(f'rival_logに保存されたkey: {self.rival_log.keys()}')
        logger.debug(f'rival_scoreのkey: {self.sdvx_logger.rival_score.keys()}')
        for i,p in enumerate(self.sdvx_logger.rival_names): # rival_log['名前']=MusicInfoのリスト
            if p not in self.rival_log.keys():
                self.rival_log[p] = []
            logger.debug(f"rival: {p} - {len(self.sdvx_logger.rival_score[p])}件")

    def save_rivallog(self):
        """ライバルの自己べ情報を保存する
        """
        for i,p in enumerate(self.sdvx_logger.rival_names):
            self.rival_log[p] = self.sdvx_logger.rival_score[p]
        with open('out/rival_log.pkl', 'wb') as f:
            pickle.dump(self.rival_log, f)

    def check_rival_update(self):
        """ライバル挑戦状用の処理。ライバルの更新有無を確認し、更新された曲一覧をdictで返す。

        Returns:
            dict: 各ライバルの更新データ。key:ライバル名(str)
        """
        out = {}
        logger.debug(f"rival_names:{self.sdvx_logger.rival_names}")
        logger.debug(f"rival_log.keys():{self.rival_log.keys()}")
        for i,p in enumerate(self.sdvx_logger.rival_names):
            if p in self.rival_log.keys():
                out[p] = []

                # pklに保存していたライバルデータに対して逆引き用dict作成
                tmp = {}
                for s in self.rival_log[p]:
                    tmp[(s.title,s.difficulty)] = s

                # 検索
                for s in self.sdvx_logger.rival_score[p]:
                    new = None
                    # 既にスコアがついてた曲の更新時
                    if (s.title, s.difficulty) in tmp.keys():
                        if s.best_score > tmp[(s.title, s.difficulty)].best_score:
                            new = [s.title, s.difficulty, s.best_score, s.best_score-tmp[(s.title, s.difficulty)].best_score]
                    else: # 新規プレイ時
                        new = [s.title, s.difficulty, s.best_score, s.best_score]
                    # 自己べよりも高い場合は出力。自分が未プレーの場合は出力されない。
                    if new != None:
                        for i,my in enumerate(self.sdvx_logger.best_allfumen):
                            if (s.title==my.title) and (s.difficulty == my.difficulty.upper()): # TODO 自己べ情報のフォーマットを揃えたい
                                if s.best_score > my.best_score:
                                    new.append(my.best_score)
                                    out[p].append(new) # title, diff, score, diffだけ保持
                                    logger.debug(f'added! {new}')
                if len(out[p]) > 0:
                    self.logToWindow(f'{self.i18n("message.rivals.chalenge.received.part",p,len(out[p]))}')
                logger.debug(f'ライバル:{p}から挑戦状が{len(out[p])}件届いています。')
            #self.rival_log[p] = self.sdvx_logger.rival_score[i] # ライバルの一時スコアを保存する場合はこれ

        with open('out/rival_updates.xml', 'w', encoding='utf-8') as f:
            f.write(f'<?xml version="1.0" encoding="utf-8"?>\n')
            f.write(f'<Updates>\n')
            for p in out.keys(): # ライバルID
                for s in out[p]: # 曲
                    f.write("<Item>\n")
                    title_esc   = s[0].replace('&', '&amp;').replace('<','&lt;').replace('>','&gt;').replace('"','&quot;').replace("'",'&apos;')
                    difficulty  = s[1]
                    score       = s[2]
                    diff        = s[3]
                    myscore     = s[4]
                    _, info = self.sdvx_logger.get_fumen_data(s[0], difficulty)
                    lv = info.lv
                    f.write(f"    <rival>{p}</rival>\n")
                    f.write(f"    <lv>{lv}</lv>\n")
                    f.write(f"    <title>{title_esc}</title>\n")
                    f.write(f"    <difficulty>{difficulty}</difficulty>\n")
                    f.write(f"    <score>{score:,}</score>\n")
                    f.write(f"    <myscore>{myscore:,}</myscore>\n")
                    f.write(f"    <score_10k>{int(score/10000):,}</score_10k>\n")
                    f.write(f"    <myscore_10k>{int(myscore/10000):,}</myscore_10k>\n")
                    f.write(f"    <behind>{score - myscore}</behind>\n")
                    f.write(f"    <behind_fmt>{score - myscore:+,}</behind_fmt>\n")
                    f.write(f"    <behind_10k>{int((score - myscore)/10000)}</behind_10k>\n")
                    f.write(f"    <behind_fmt_10k>{int((score - myscore)/10000):+,}</behind_fmt_10k>\n")
                    f.write("</Item>\n")
            f.write(f'</Updates>\n')
        return out

    def update_rival(self):
        try:
            self.update_mybest()
            self.sdvx_logger.get_rival_score(self.settings['player_name'], self.settings['rival_names'], self.settings['rival_googledrive'])
            self.logToWindow(self.i18n('message.rivals.data.completed'))
            self.check_rival_update()
        except Exception:
            logger.debug(traceback.format_exc())
            self.logToWindow(self.i18n('message.rivals.data.failed')) # ネットワーク接続やURL設定を見直す必要がある
            
    def capture_volforce_btn(self, vf_capture=None, class_capture=None):
        capture_volforce(vf_capture,class_capture)
        self.logToWindow(self.i18n('message.screenshot.save.volforce'))
            
    def capture_volforce(self, vf_capture=None, class_capture=None):
        
        if self.detect_mode != detect_mode.result :
            self.logToWindow(self.i18n('message.screenshot.save.volforce.fail'))
            return
        
        if vf_capture is None :
            vf_cur = self.img_rot.crop(self.get_detect_points('vf'))
        else :
            vf_cur = vf_capture
            
        if class_capture is None :
            class_cur = self.img_rot.crop(self.get_detect_points('class'))
        else :
            class_cur = class_capture
            
        vf_cur.save('out/vf_cur.png')
        class_cur.save('out/class_cur.png')
        
        #self.logToWindow(self.i18n('message.screenshot.save.volforce'))
    
    def capture_summary_btn(self):
        self.gen_summary.generate()  
        self.logToWindow(self.i18n('message.screenshot.save.summary'))  

    def save_playerinfo(self):
        """プレイヤー情報(VF,段位)を切り出して画像として保存する。
        """
        
        #if hash_function is None :
        hash_function = imagehash.phash
        
        # Sleep for 0.5 seconds just to make sure we don't spam the capture but still get a decent screenshot result on average
        time.sleep(0.5)
        
        
        vf_cur = self.img_rot.crop(self.get_detect_points('vf'))
        class_cur = self.img_rot.crop(self.get_detect_points('class'))
        
        threshold = 1400000 if self.settings['save_on_capture'] else 700000
        if np.array(vf_cur).sum() > threshold or self.settings['always_update_vf']:
            
            save_vf = False
            
            # If the hash of the last capture VF is the same as this one, don't save it
            if self.last_vf_hash is None :
                 self.last_vf_hash = hash_function(vf_cur,10)
                 save_vf = True
                 #self.logToWindow(f"No previous VF hash. Last VF hash is now {self.last_vf_hash}")
            else  :   
                 vf_cur_hash = hash_function(vf_cur,10)
                 if abs(self.last_vf_hash - vf_cur_hash) > 2 :
                    #self.logToWindow(f"VF hash differ from last. Last: {self.last_vf_hash} | New: {vf_cur_hash}.")
                    self.last_vf_hash = vf_cur_hash
                    save_vf = True

            if save_vf :
                self.capture_volforce(vf_cur,class_cur)    
                
            if not self.gen_first_vf: # 本日1プレー目に保存しておく
                vf_cur.save('out/vf_pre.png')
                class_cur.save('out/class_pre.png')
                self.gen_first_vf = True
    
    
    def add_result_to_playlist(self, result:OnePlayData):
        self.session_plays.append(result)
        
    def send_playlist(self):
        
        if len(self.session_plays) > 0 :
            for i in range(len(self.settings['webhook_names'])):
    
                if self.settings['webhook_playlist'][i]:
                    msg = f'Session playlist for {self.settings["webhook_player_name"]} ({len(self.session_plays)} songs):\n'
                    
                    for j, play in enumerate(self.session_plays) :
                        msg += f'{int(j+1):0>2} - {play.title}\n'
                    
                    webhook = DiscordWebhook(url=self.settings['webhook_urls'][i], username=f"{self.settings['webhook_player_name']}")
                    webhook.content=msg
                    
                    try:
                        res = webhook.execute()
                    except Exception:
                        self.logToWindow(f'{self.i18n("message.error.webhook.send")}')
                        logger.debug(traceback.format_exc())
        

    def start_rta_mode(self):
        """RTA開始処理。変数の初期化などを行う。
        """
        self.rta_mode = True
        self.rta_finished = False
        self.rta_starttime = datetime.datetime.now()
        self.rta_logger = SDVXLogger(player_name=self.settings['player_name'], rta_mode=True)
        self.rta_target_vf = Decimal(self.settings['rta_target_vf'])
        rta_vf_str = f"{self.settings['obs_txt_vf_header']}0.000{self.settings['obs_txt_vf_footer']}"
        self.obs.change_text('sdvx_helper_rta_vf', rta_vf_str)
        self.logToWindow(f'{self.i18n("message.rta.started")}\n{self.i18n("message.rta.target")} = {self.rta_target_vf}')

    def get_capture_after_rotate(self):
        """ゲーム画面のキャプチャを取得し、正しい向きに直す。self.img_rotにも格納する。

        Returns:
            PIL.Image: 取得したゲーム画面
        """
        while True:
            try:
                if self.settings['save_on_capture']:
                    self.obs.save_screenshot()
                    img = Image.open(self.imgpath)
                else:
                    img = self.obs.get_screenshot()
                if self.settings['orientation_top'] == 'right':
                    ret = img.rotate(90, expand=True)
                elif self.settings['orientation_top'] == 'left':
                    ret = img.rotate(270, expand=True)
                else:
                    ret = img.resize((1080,1920))
                break
            except Exception:
                continue
        self.img_rot = ret
        return ret
    
    def update_settings(self, ev, val):
        """GUIから値を取得し、設定の更新を行う。

        Args:
            ev (str): sgのイベント
            val (dict): sgの各GUIの値
        """
        if self.gui_mode == gui_mode.main:
            if self.settings['clip_lxly']:
                self.settings['lx'] = max(0, self.window.current_location()[0])
                self.settings['ly'] = max(0, self.window.current_location()[1])
            elif not self.window.is_closed():
                self.settings['lx'] = self.window.current_location()[0]
                self.settings['ly'] = self.window.current_location()[1]
        elif self.gui_mode == gui_mode.webhook:
            self.settings['webhook_player_name'] = val['player_name2']
        elif self.gui_mode == gui_mode.googledrive:
            self.settings['get_rival_score'] = val['get_rival_score']
            self.settings['update_rival_on_result'] = val['update_rival_on_result']
            self.settings['player_name'] = val['player_name3']
        elif self.gui_mode == gui_mode.setting:
            self.settings['clip_lxly'] = val['clip_lxly']
            self.settings['host'] = val['input_host']
            self.settings['port'] = val['input_port']
            self.settings['passwd'] = val['input_passwd']
            if val['orientation_top_right']:
                self.settings['orientation_top'] = 'right'
            elif val['orientation_top_top']:
                self.settings['orientation_top'] = 'top'
            elif val['orientation_top_left']:
                self.settings['orientation_top'] = 'left'
            self.settings['autosave_always'] = val['chk_always']
            self.settings['ignore_rankD'] = val['chk_ignore_rankD']
            self.settings['auto_update'] = val['chk_auto_update']
            #self.settings['obs_txt_plays'] = val['obs_txt_plays']
            self.settings['obs_txt_plays_header'] = val['obs_txt_plays_header']
            self.settings['obs_txt_plays_footer'] = val['obs_txt_plays_footer']
            self.settings['alert_blastermax'] = val['alert_blastermax']
            self.settings['logpic_bg_alpha'] = val['logpic_bg_alpha']
            self.settings['rta_target_vf'] = val['rta_target_vf']
            self.settings['player_name'] = val['player_name']
            self.sdvx_logger.player_name = val['player_name']
            self.settings['save_on_capture'] = val['save_on_capture']
            self.settings['save_jacketimg'] = val['save_jacketimg']
            self.settings['import_from_select'] = val['import_from_select']
            self.settings['import_arcade_score'] = val['import_arcade_score']
            self.settings['autosave_prewait'] = val['autosave_prewait']
            self.settings['always_update_vf'] = val['update_vf']
            self.settings['enable_discord_presence'] = val['update_discord_presence']

    def build_layout_one_scene(self, name, LR=None):
        """OBS制御設定画面におけるシーン1つ分のGUIを出力する。

        Args:
            name (str): シーン名
            LR (bool, optional): 開始、終了があるシーンかどうかを指定。 Defaults to None.

        Returns:
            list: pysimpleguiで使うレイアウトを格納した配列。
        """
        if LR == None:
            sc = [
                    sg.Column([[par_text(self.i18n('text.obs.view'))],[sg.Listbox(self.settings[f'obs_enable_{name}'], key=f'obs_enable_{name}', size=(20,4))], [par_btn('add', key=f'add_enable_{name}'),par_btn('del', key=f'del_enable_{name}')]]),
                    sg.Column([[par_text(self.i18n('text.obs.delete'))],[sg.Listbox(self.settings[f'obs_disable_{name}'], key=f'obs_disable_{name}', size=(20,4))], [par_btn('add', key=f'add_disable_{name}'),par_btn('del', key=f'del_disable_{name}')]]),
                ]
        else:
            scL = [[
                    sg.Column([[par_text(self.i18n('text.obs.view'))],[sg.Listbox(self.settings[f'obs_enable_{name}0'], key=f'obs_enable_{name}0', size=(20,4))], [par_btn('add', key=f'add_enable_{name}0'),par_btn('del', key=f'del_enable_{name}0')]]),
                    sg.Column([[par_text(self.i18n('text.obs.delete'))],[sg.Listbox(self.settings[f'obs_disable_{name}0'], key=f'obs_disable_{name}0', size=(20,4))], [par_btn('add', key=f'add_disable_{name}0'),par_btn('del', key=f'del_disable_{name}0')]]),
                ]]
            scR = [[
                    sg.Column([[par_text(self.i18n('text.obs.view'))],[sg.Listbox(self.settings[f'obs_enable_{name}1'], key=f'obs_enable_{name}1', size=(20,4))], [par_btn('add', key=f'add_enable_{name}1'),par_btn('del', key=f'del_enable_{name}1')]]),
                    sg.Column([[par_text(self.i18n('text.obs.delete'))],[sg.Listbox(self.settings[f'obs_disable_{name}1'], key=f'obs_disable_{name}1', size=(20,4))], [par_btn('add', key=f'add_disable_{name}1'),par_btn('del', key=f'del_disable_{name}1')]]),
                ]]
            sc = [
                sg.Frame(self.i18n('text.obs.start'), scL, title_color='#440000'),sg.Frame(self.i18n('text.obs.end'), scR, title_color='#440000')
            ]
        ret = [
            [
                par_text(f'{self.i18n("text.obs.scene")}:')
                ,par_text(self.settings[f'obs_scene_{name}'], size=(20, 1), key=f'obs_scene_{name}')
                ,par_btn('set', key=f'set_scene_{name}')
            ],
            sc
        ]
        return ret

    def gui_webhook(self):
        """カスタムWebhook設定画面のGUIを起動する。
        """
        self.gui_mode = gui_mode.init
        if self.window:
            self.window.close()

        layout_lvs = [
            [sg.Checkbox('all', key='webhook_enable_alllv', enable_events=True)]+[sg.Checkbox(f'{lv}', key=f'webhook_enable_lv{lv}') for lv in range(1,11)],
            [sg.Checkbox(f'{lv}', key=f'webhook_enable_lv{lv}') for lv in range(11,14)]+[sg.Checkbox(f'{lv}', key=f'webhook_enable_lv{lv}', default=True) for lv in range(14,21)]
        ]
        layout_lamps = [
            [
                sg.Checkbox('all', key='webhook_enable_alllamp', enable_events=True),
                sg.Checkbox('PUC', key='webhook_enable_puc', default=True),
                sg.Checkbox('UC', key='webhook_enable_uc',default=True),
                sg.Checkbox('MAXXIVE', key='webhook_enable_exh', default=True),
                sg.Checkbox('EXC', key='webhook_enable_hard', default=True),
                sg.Checkbox('COMP', key='webhook_enable_clear', default=True),
                sg.Checkbox('Failed', key='webhook_enable_failed'),
            ]
        ]
        layout = [
            [sg.Text(self.i18n('text.webhook.playername')), sg.Input(self.settings['webhook_player_name'], key='player_name2')],
            [sg.Listbox(self.settings['webhook_names'], size=(50, 5), key='list_webhook', enable_events=True), sg.Button(self.i18n('button.webhook.add'), key='webhook_add', tooltip=self.i18n('button.webhook.add.tooltip')), sg.Button(self.i18n('button.webhook.delete'), key='webhook_del')],
            [sg.Text(self.i18n('text.webhook.settings.name')), sg.Input('', key='webhook_names', size=(63,1))],
            [sg.Text(self.i18n('text.webhook.url')), sg.Input('', key='webhook_urls', size=(50,1))],
            [sg.Checkbox(self.i18n('checkbox.webhook.send.images'), key='webhook_enable_pics', default=True),sg.Check(self.i18n('text.weebhook.send.playlist'), key='webhook_playlist')],
            [sg.Frame(self.i18n('text.webhook.target.level'), layout=layout_lvs, title_color='#000044')],
            [sg.Frame(self.i18n('text.webhook.target.lamp'), layout=layout_lamps, title_color='#000044')],
        ]

        self.gui_mode = gui_mode.webhook
        self.window = sg.Window(f"{self.i18n('window.webhook.title')}", layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']))

    def gui_googledrive(self):
        """Googleドライブ連携設定用のGUIを起動する。
        """
        self.gui_mode = gui_mode.init
        if self.window:
            self.window.close()
        layout_list = [
            [sg.Table([[self.settings['rival_names'][i], self.settings['rival_googledrive'][i]] for i in range(len(self.settings['rival_names']))], key='rival_names', auto_size_columns=False, headings=['name', 'gdrive_id'], size=(30,7), col_widths=[15, 30], justification='left', enable_events=True)],
        ]
        layout_btn = [
            [par_btn(self.i18n('button.rivals.add'), key='add_rival')],
            [par_btn(self.i18n('button.rivals.delete'), key='del_rival')],
            [par_btn(self.i18n('button.rivals.url'), key='open_rival')],
            #[par_btn('上書き', key='mod_rival')],
        ]
        layout = [
            [sg.Text(self.i18n('text.rivals.playername')), sg.Input(self.settings['player_name'], key='player_name3')],
            [par_text(self.i18n('text.rivals.automatic.save.destination')), par_btn(self.i18n('button.rivals.change'), key='btn_my_googledrive')],
            [par_text(self.settings['my_googledrive'], key='txt_my_googledrive')],
            [sg.Checkbox(self.i18n('checkbox.rivals.getScoreAtStart'),self.settings['get_rival_score'],key='get_rival_score', enable_events=True)],
            [sg.Checkbox(self.i18n('checkbox.rivals.updateDataEverytime'),self.settings['update_rival_on_result'],key='update_rival_on_result', enable_events=True)],
            [par_text(self.i18n('text.rivals.rivalName')), sg.Input('', key='rival_name', size=(30,1))],
            [par_text(self.i18n('text.rivals.rivalURL')), sg.Input('', key='rival_googledrive')],
            [sg.Column(layout_list), sg.Column(layout_btn)]
        ]
        self.gui_mode = gui_mode.googledrive
        self.window = sg.Window(self.i18n('window.rivals.title'), layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']))

    def gui_obs_control(self):
        """OBS制御設定画面のGUIを起動する。
        """
        self.gui_mode = gui_mode.init
        if self.window:
            self.window.close()
        obs_scenes = []
        obs_sources = []
        if self.obs != False:
            tmp = self.obs.get_scenes()
            tmp.reverse()
            for s in tmp:
                obs_scenes.append(s['sceneName'])
        layout_select = self.build_layout_one_scene('select', 0)
        layout_play = self.build_layout_one_scene('play', 0)
        layout_result = self.build_layout_one_scene('result', 0)
        layout_boot = self.build_layout_one_scene('boot')
        layout_quit = self.build_layout_one_scene('quit')
        layout_obs2 = [
            [par_text(f'{self.i18n("text.obs.settings.sceneCollection")}:'), sg.Combo(self.obs.get_scene_collection_list(), key='scene_collection', size=(40,1), enable_events=True)],
            [par_text(f'{self.i18n("text.obs.settings.scene")}:'), sg.Combo(obs_scenes, key='combo_scene', size=(40,1), enable_events=True)],
            [par_text(f'{self.i18n("text.obs.settings.source")}:'),sg.Combo(obs_sources, key='combo_source', size=(40,1))],
            [par_text(f'{self.i18n("text.obs.settings.gameScene")}:'), par_text(self.settings['obs_source'], size=(20,1), key='obs_source'), par_btn('set', key='set_obs_source')],
            [sg.Frame(self.i18n("text.obs.settings.songSelection"),layout=layout_select, title_color='#000044')],
            [sg.Frame(self.i18n("text.obs.settings.duringPlay"),layout=layout_play, title_color='#000044')],
            [sg.Frame(self.i18n("text.obs.settings.resultScreen"),layout=layout_result, title_color='#000044')],
        ]
        layout_r = [
            [sg.Frame(self.i18n('text.obs.settings.keyStrokeActivation.start'), layout=layout_boot, title_color='#000044')],
            [sg.Frame(self.i18n('text.obs.settings.keyStrokeActivation.end'), layout=layout_quit, title_color='#000044')],
        ]

        col_l = sg.Column(layout_r)
        col_r = sg.Column(layout_obs2)

        layout = [
            [col_l, col_r],
            [sg.Text('', key='info', font=(None,9))]
        ]
        self.gui_mode = gui_mode.obs_control
        self.window = sg.Window(self.i18n('window.obs.settings.title'), layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']))
        if self.settings['obs_scene_collection'] != '':
            self.window['scene_collection'].update(value=self.settings['obs_scene_collection'])

    def gui_setting(self):
        """設定画面のGUIを起動する。
        """
        self.gui_mode = detect_mode.init
        if self.window:
            self.window.close()
        layout_obs = [
            [par_text(f'{self.i18n("text.settings.obsHost")}: '), sg.Input(self.settings['host'], font=FONT, key='input_host', size=(20,20))],
            [par_text(f'{self.i18n("text.settings.obsPort")}: '), sg.Input(self.settings['port'], font=FONT, key='input_port', size=(10,20))],
            [par_text(f'{self.i18n("text.settings.obsPassword")}: '), sg.Input(self.settings['passwd'], font=FONT, key='input_passwd', size=(20,20), password_char='*')],
        ]
        layout_gamemode = [
            [par_text(self.i18n('text.settings.screenOrientation.title')),
             sg.Radio(self.i18n('text.settings.screenOrientation.right'), group_id='topmode',default=self.settings['orientation_top']=='right', enable_events=True, key='orientation_top_right'),
             sg.Radio(self.i18n('text.settings.screenOrientation.none'), group_id='topmode', default=self.settings['orientation_top']=='top', enable_events=True, key='orientation_top_top'),
             sg.Radio(self.i18n('text.settings.screenOrientation.left'), group_id='topmode', default=self.settings['orientation_top']=='left', enable_events=True, key='orientation_top_left'),
            ],
        ]
        list_vf = [f"{i}.000" for i in range(1,17)]
        list_vf += [z for sublist in [[x, y] for x, y in zip([f'{i}.000' for i in range(17,23)], [f'{i}.500' for i in range(17,23)])] for z in sublist]
        layout_etc = [
            [sg.Checkbox(self.i18n('text.settings.saveOnCapture'), self.settings['save_on_capture'], key='save_on_capture', enable_events=True, tooltip=self.i18n('text.settings.saveOnCapture.tooltip'))],
            [par_text(self.i18n('text.settings.resultsAutoSaveFolder')), par_btn(self.i18n('button.settings.change'), key='btn_autosave_dir')],
            [sg.Text(self.settings['autosave_dir'], key='txt_autosave_dir')],
            [sg.Checkbox(self.i18n('checkbox.settings.autoSaveAlways'),self.settings['autosave_always'],key='chk_always', enable_events=True), par_text(self.i18n('text.settings.screenshotDelay'), font=(None,10), tooltip=self.i18n('text.settings.screenshotDelay.tooltip')),sg.Spin([f"{i/10:.1f}" for i in range(100)], self.settings['autosave_prewait'], readonly=True, key='autosave_prewait', size=(4,1))],
            [sg.Checkbox(self.i18n('checkbox.settings.ignoreRankD'),self.settings['ignore_rankD'],key='chk_ignore_rankD', enable_events=True)],
            [sg.Button(self.i18n('button.settings.processPastResults'), key='read_from_result')],
            [sg.Button(self.i18n('button.settings.generateJackets'), key='gen_jacket_imgs')], 
            [sg.Checkbox(self.i18n('checkbox.settings.autoSaveCover'), self.settings['save_jacketimg'], key='save_jacketimg')],
            [
                sg.Text(self.i18n('text.settings.textNumberOfNumbersPlayed'), tooltip=f'{self.i18n("text.settinss.textNumberOfNumbersPlayed.tooltip",self.settings["obs_txt_plays"])}'),
                sg.Text(self.i18n('text.settings.textNumberOfNumbersPlayed.prefix'), tooltip=self.i18n('text.settings.textNumberOfNumbersPlayed.prefix.tooltip')),sg.Input(self.settings['obs_txt_plays_header'], key='obs_txt_plays_header', size=(10,1)),
                sg.Text(self.i18n('text.settings.textNumberOfNumbersPlayed.suffix'), tooltip=self.i18n('text.settings.textNumberOfNumbersPlayed.suffix.tooltip')), sg.Input(self.settings['obs_txt_plays_footer'], key='obs_txt_plays_footer', size=(10,1)),
            ],
            [
                sg.Text(self.i18n('text.settings.textPlayTime'), tooltip=f'{self.i18n("text.settings.textPlayTime.tooltip1")} {self.settings["obs_txt_playtime"]} {self.i18n("text.settings.textPlayTime.tooltip1")}'),
                sg.Text(self.i18n('text.settings.textPlayTime.prefix'), tooltip={self.i18n("text.settings.textPlayTime.prefix.tooltip")}),sg.Input(self.settings['obs_txt_playtime_header'], key='obs_txt_playtime_header', size=(10,1)),
                #sg.Text(self.i18n('text.settings.textPlayTime.suffix'), tooltip={self.i18n("text.settings.textPlayTime.suffix.tooltip")}), sg.Input(self.settings['obs_txt_plays_footer'], key='obs_txt_plays_footer', size=(10,1)),
            ],
            [
                par_text(self.i18n('text.settings.rta.title')), par_text(self.i18n('text.settings.rta.target')), sg.Combo(list_vf, key='rta_target_vf', default_value=self.settings['rta_target_vf'], enable_events=True)
            ],
            [sg.Checkbox(self.i18n('checkbox.settings.blasterGaugeMax'),self.settings['alert_blastermax'],key='alert_blastermax', enable_events=True)],
            [sg.Text(self.i18n('text.settings.logWindowTransparency')), sg.Combo([i for i in range(256)],default_value=self.settings['logpic_bg_alpha'],key='logpic_bg_alpha', enable_events=True)],
            [sg.Checkbox(self.i18n('checkbox.settings.checkForUpdatesAtStart'),self.settings['auto_update'],key='chk_auto_update', enable_events=True)],
            [sg.Text(self.i18n('text.settings.statsPlayerName')),sg.Input(self.settings['player_name'], key='player_name', size=(30,1))],
            [sg.Checkbox(self.i18n('checkbox.settings.importFromSelect'),self.settings['import_from_select'],key='import_from_select', enable_events=True),sg.Checkbox(self.i18n('checkbox.settings.includeArcadeScores'),self.settings['import_arcade_score'],key='import_arcade_score', enable_events=True)],
            [sg.Checkbox(self.i18n('checkbox.settings.correctWindowsCoordinates'),self.settings['clip_lxly'],key='clip_lxly', enable_events=True, tooltip=self.i18n('checkbox.settings.correctWindowsCoordinates.tooltip'))],
            [sg.Checkbox(self.i18n('checkbox.settings.allwaysUpdateVF'),self.settings['always_update_vf'],key='update_vf', enable_events=True, tooltip=self.i18n('checkbox.settings.allwaysUpdateVF.tooltip'))],
            [sg.Checkbox(self.i18n('checkbox.settings.discordPresence'),self.settings['enable_discord_presence'],key='update_discord_presence', enable_events=True, tooltip=self.i18n('checkbox.settings.discordPresence.tooltip'))],
        ]
        layout = [
            [sg.Frame(self.i18n('text.settings.obsSettings.title'), layout=layout_obs, title_color='#000044')],
            [sg.Frame(self.i18n('text.settings.gameSettings.title'), layout=layout_gamemode, title_color='#000044')],
            [sg.Frame(self.i18n('text.settings.otherSettings.title'), layout=layout_etc, title_color='#000044')],
        ]
        self.gui_mode = gui_mode.setting
        self.window = sg.Window(self.i18n('window.settings.title'), layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']))

    def gui_main(self):
        """メイン画面のGUIを起動する。
        """
        self.gui_mode = detect_mode.init
        self.detect_mode = detect_mode.init
        if self.window:
            self.window.close()
        menuitems = [
            [self.i18n('menu.file'),[self.i18n('menu.file.settings'),self.i18n('menu.file.obs'), self.i18n('menu.file.webhook'), self.i18n('menu.file.updates')]],
            [self.i18n('menu.rivals'),[self.i18n('menu.rivals.google'), self.i18n('menu.rivals.get')]],
            [self.i18n('menu.rta'),[self.i18n('menu.rta.start')]],
            [self.i18n('menu.analysis'),[self.i18n('menu.analysis.tweet'), self.i18n('menu.analysis.csv'), self.i18n('menu.analysis.csvBest')]]
        ]
        layout_shortcuts_0 = [
                [par_text(f'F1 - {self.i18n("text.main.shortcut.f1")}',font=(None, 9))],
                [par_text(f'F2 - {self.i18n("text.main.shortcut.f2")}',font=(None, 9))],
                [par_text(f'F3 - {self.i18n("text.main.shortcut.f3")}',font=(None, 9))], 
            ]
        
        layout_shortcuts_1 = [
                [par_text(f'F4 - {self.i18n("text.main.shortcut.f4")}',font=(None, 9))],
                [par_text(f'F5 - {self.i18n("text.main.shortcut.f5")}',font=(None, 9))],
                [par_text(f'F6 - {self.i18n("text.main.shortcut.f6")}',font=(None, 9))], 
            ]
        layout_shortcuts_2 = [
                [par_text(f'F7 - {self.i18n("text.main.shortcut.f7")}',font=(None, 9))],
                [par_text(f'F8 - {self.i18n("text.main.shortcut.f8")}',font=(None, 9))], 
                [par_text(f'F9 - {self.i18n("text.main.shortcut.f9")}',font=(None, 9))],
            ]
        layout_buttons = [
                [
                    par_btn(self.i18n('button.main.save'), tooltip=self.i18n('button.main.save.tooltip'), key='btn_savefig', size=(5,3)),
                    par_btn(self.i18n('button.main.save.summary'), tooltip=self.i18n('button.main.save.summary.tooltip'), key='btn_save_summary' , size=(8,3)),
                    par_btn(self.i18n('button.main.save.volforce'), tooltip=self.i18n('button.main.save.volforce.tooltip'), key='btn_save_vf', size=(5,3)),
                ]
            ] 
        layout = [
            [sg.Menubar(menuitems, key='menu')],
            [
                sg.Text('Language/言語', font=(None,12)),
                sg.Combo(self.bundle.get_available_bundles(), key='locale', font=(None,12), default_value=self.default_locale,enable_events=True),
                sg.Button(self.i18n('button.exit'), font=(None,12), key="btn_exit")                
            ],
            [
                par_text(f'{self.i18n("text.main.plays")}:'), par_text(str(self.plays), key='txt_plays'),
                par_text(f'{self.i18n("text.main.mode")}:'), par_text(self.detect_mode.name, key='txt_mode'),
                par_text(self.i18n('message.main.obsError'), key='txt_obswarning', text_color="#ff0000"),
            ],
            [
                sg.Frame(title='',layout=layout_buttons, border_width=0,font=(None, 9),vertical_alignment='top'),
                sg.Frame(title='',layout=layout_shortcuts_0, border_width=0,font=(None, 9)),
                sg.Frame(title='',layout=layout_shortcuts_1, border_width=0,font=(None, 9)),
                sg.Frame(title='',layout=layout_shortcuts_2, border_width=0,font=(None, 9))
            ],
            [par_text('', size=(40,1), key='txt_info')],
        ]
        if self.settings['dbg_enable_output']:
            layout.append([sg.Multiline(size=(100,8), key='output', font=(None, 9),expand_x=True)])
        self.gui_mode = gui_mode.main
        
        self.window = sg.Window(self.i18n('window.main.title',SWVER), layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']))
        
        if self.connect_obs():
            self.window['txt_obswarning'].update('')
        
        if self.settings['enable_discord_presence']: 
            self.setup_discord_presence()

    def start_detect(self):
        """認識スレッドを開始する。
        """
        logger.debug('starting detect thread')
        self.stop_thread = False
        self.th = threading.Thread(target=self.detect, daemon=True)
        self.th.start()

    def stop_detect(self):
        """認識スレッドを停止する。
        """
        logger.debug('stopping detect thread')
        if self.th != False:
            self.stop_thread = True
            self.th.join()
            self.stop_thread = False
            self.th = False

    def play_wav(self, filename:str):
        """指定した音声ファイルを再生する。

        Args:
            filename (str): 再生したいファイル名(フルパス)
        """
        try:
            winsound.PlaySound(filename, winsound.SND_FILENAME)
        except:
            logger.debug(traceback.format_exc())

    def connect_obs(self):
        if self.obs != False:
            self.obs.close()
            self.obs = False
        try:
            self.obs = OBSSocket(self.settings['host'], self.settings['port'], self.settings['passwd'], self.settings['obs_source'], self.imgpath)
            if self.gui_mode == gui_mode.main:
                self.window['txt_obswarning'].update('')
                self.logToWindow(self.i18n('message.obs.connect'))
            return True
        except:
            logger.debug(traceback.format_exc())
            self.obs = False
            self.logToWindow('obs socket error!')
            if self.gui_mode == gui_mode.main:
                self.window['txt_obswarning'].update(self.i18n('message.main.obsError'))
                self.logToWindow(self.i18n('message.obs.error'))
            return False

    def control_obs_sources(self, name:str):
        """OBSソースの表示・非表示及びシーン切り替えを行う。
        nameで適切なシーン名を指定する必要がある。

        Args:
            name (str): シーン名(boot,exit,play{0,1},select{0,1},result{0,1})

        Returns:
            bool: 正常終了していればTrue
        """
        if self.gui_mode == gui_mode.main:
            self.window['txt_mode'].update(self.detect_mode.name)
        if self.obs == False:
            logger.debug('cannot connect to OBS -> exit')
            return False
        logger.debug(f"name={name} (detect_mode={self.detect_mode.name})")
        name_common = name
        if name[-1] in ('0','1'):
            name_common = name[:-1]
        scene = self.settings[f'obs_scene_{name_common}']
        # TODO 前のシーンと同じなら変えないようにしたい
        if scene != '':
            self.obs.change_scene(scene)
        # 非表示の制御
        for s in self.settings[f"obs_disable_{name}"]:
            tmps, tmpid = self.obs.search_itemid(scene, s)
            self.obs.disable_source(tmps,tmpid)
            #print('disable', scene, s, tmps, tmpid)
        # 表示の制御
        for s in self.settings[f"obs_enable_{name}"]:
            tmps, tmpid = self.obs.search_itemid(scene, s)
            #self.obs.refresh_source(s)
            self.obs.enable_source(tmps,tmpid)
            #print('enable', scene, s, tmps, tmpid)
        return True
    
    def is_onselect(self):
        """現在の画面が選曲画面かどうか判定し、結果を返す

        Returns:
            bool: 選曲画面かどうか
        """
        img = self.img_rot.crop(self.get_detect_points('onselect'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/onselect.png')
        hash_target = imagehash.average_hash(img)
        ret = abs(hash_target - tmp) < 5
        #logger.debug(f'onselect diff:{abs(hash_target-tmp)}')
        return ret

    def is_onresult(self):
        """現在の画面がリザルト画面かどうか判定し、結果を返す

        Returns:
            bool: リザルト画面かどうか
        """
        cr = self.img_rot.crop(self.get_detect_points('onresult_val0'))
        tmp = imagehash.average_hash(cr)
        img_j = Image.open('resources/images/onresult.png')
        hash_target = imagehash.average_hash(img_j)
        val0 = abs(hash_target - tmp) <5 

        cr = self.img_rot.crop(self.get_detect_points('onresult_val1'))
        tmp = imagehash.average_hash(cr)
        img_j = Image.open('resources/images/onresult2.png')
        hash_target = imagehash.average_hash(img_j)
        val1 = abs(hash_target - tmp) < 5

        ret = val0 & val1
        if self.params['onresult_enable_head']:
            cr = self.img_rot.crop(self.get_detect_points('onresult_head'))
            tmp = imagehash.average_hash(cr)
            img_j = Image.open('resources/images/result_head.png')
            hash_target2 = imagehash.average_hash(img_j)
            val2 = abs(hash_target2 - tmp) < 5
            ret &= val2

        return ret

    def is_onplay(self):
        """現在の画面がプレー画面かどうか判定し、結果を返す

        Returns:
            bool: プレー画面かどうか
        """
        img = self.img_rot.crop(self.get_detect_points('onplay_val1'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/onplay1.png')
        hash_target = imagehash.average_hash(img)
        ret1 = abs(hash_target - tmp) < 10
        img = self.img_rot.crop(self.get_detect_points('onplay_val2'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/onplay2.png')
        hash_target = imagehash.average_hash(img)
        ret2 = abs(hash_target - tmp) < 10
        return ret1&ret2

    def is_ondetect(self):
        """現在の画面が曲決定画面かどうか判定し、結果を返す

        Returns:
            bool: 曲決定画面かどうか
        """
        img = self.img_rot.crop(self.get_detect_points('ondetect'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/ondetect.png')
        hash_target = imagehash.average_hash(img)
        ret = abs(hash_target - tmp) < 10
        return ret
    
    def is_onlogo(self):
        """現在の画面が遷移画面(ゲームタイトルロゴ)画面かどうか判定し、結果を返す

        Returns:
            bool: 遷移画面(ゲームタイトルロゴ)画面かどうか
        """
        img = self.img_rot.crop(self.get_detect_points('onlogo'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/logo.png')
        hash_target = imagehash.average_hash(img)
        ret = abs(hash_target - tmp) < 10
        return ret
    
    def get_detect_points(self, name:str):
        """self.paramsのパラメータ名を受け取り、四隅の座標を算出して返す

        Args:
            name (str): パラメータ名。params.jsonのパラメータ名のうち、_sxなどを含まない部分を指定すること。

        Returns:
            (int,int,int,int): sx,sy,ex,eyの4座標
        """
        sx = self.params[f'{name}_sx']
        sy = self.params[f'{name}_sy']
        ex = self.params[f'{name}_sx']+self.params[f'{name}_w']-1
        ey = self.params[f'{name}_sy']+self.params[f'{name}_h']-1
        return (sx,sy,ex,ey)
    
    def chk_blastermax(self) -> bool:
        """Blaster Gaugeが最大かどうかを検出する。

        Returns:
            bool: 最大ならTrue
        """
        img = self.img_rot.crop(self.get_detect_points('blastermax'))
        tmp = imagehash.average_hash(img)
        img = Image.open('resources/images/blastermax.png')
        hash_target = imagehash.average_hash(img)
        ret = abs(hash_target - tmp) < 10
        self.is_blastermax = ret
        return ret
    
    def webhook_add(self, val:dict):
        """カスタムwebhookを登録する

        Args:
            val (dict): pysimpleguiのwindow.read()で貰えるval
        """
        if self.window['webhook_names'] == '':
            sg.popup_ok(self.i18n('popup.settingName'))
        else:
            if self.window['webhook_urls'] == '':
                sg.popup_ok(self.i18n('popup.webwhookURL'))
            else: # 登録実行
                if val['webhook_names'] in self.settings['webhook_names']: # 上書きの場合
                    idx = self.settings['webhook_names'].index(val['webhook_names'])
                    self.settings['webhook_names'][idx] = val['webhook_names']
                    self.settings['webhook_urls'][idx] = val['webhook_urls']
                    self.settings['webhook_enable_pics'][idx] = val['webhook_enable_pics']
                    self.settings['webhook_playlist'][idx] = val['webhook_playlist']
                    self.settings['webhook_enable_lvs'][idx] = [val[f'webhook_enable_lv{lv}'] for lv in range(1,21)]
                    self.settings['webhook_enable_lamps'][idx] = [val[f'webhook_enable_{l}'] for l in ('puc', 'uc', 'exh', 'hard', 'clear', 'failed')]
                else:
                    self.settings['webhook_names'].append(val['webhook_names'])
                    self.settings['webhook_urls'].append(val['webhook_urls'])
                    self.settings['webhook_enable_pics'].append(val['webhook_enable_pics'])
                    self.settings['webhook_playlist'].append(val['webhook_playlist'])
                    self.settings['webhook_enable_lvs'].append([val[f'webhook_enable_lv{lv}'] for lv in range(1,21)])
                    self.settings['webhook_enable_lamps'].append([val[f'webhook_enable_{l}'] for l in ('puc', 'uc', 'exh', 'hard', 'clear', 'failed')])
                self.set_webhook_ui_default()

    def webhook_del(self, val:dict):
        """登録されたカスタムwebhook情報を削除する。

        Args:
            val (dict): pysimpleguiのwindow.read()で貰えるval
        """
        if len(val['list_webhook']) > 0:
            idx = self.settings['webhook_names'].index(val['list_webhook'][0])
            self.settings['webhook_names'].pop(idx)
            self.settings['webhook_urls'].pop(idx)
            self.settings['webhook_enable_pics'].pop(idx)
            self.settings['webhook_playlist'].pop(idx)
            self.settings['webhook_enable_lvs'].pop(idx)
            self.settings['webhook_enable_lamps'].pop(idx)
            self.set_webhook_ui_default()

    def webhook_read(self, val:dict):
        """登録済みのカスタムwebhook情報を読み出してGUIに反映する。

        Args:
            val (dict): pysimpleguiのwindow.read()で貰えるval
        """
        if len(val['list_webhook']) > 0:
            key = val['list_webhook'][0]
            idx = self.settings['webhook_names'].index(key)
            self.window['webhook_names'].update(key)
            self.window['webhook_urls'].update(self.settings['webhook_urls'][idx])
            self.window['webhook_enable_pics'].update(self.settings['webhook_enable_pics'][idx])
            
            # For old already saved webhooks that did not have the option before
            if idx not in self.settings['webhook_playlist']:
                self.settings['webhook_playlist'].append(False)
            
            self.window['webhook_playlist'].update(self.settings['webhook_playlist'][idx])
            
            for i in range(1,21):
                self.window[f'webhook_enable_lv{i}'].update(self.settings['webhook_enable_lvs'][idx][i-1])
            
            if len(self.settings['webhook_enable_lamps'][idx]) < 6 :
                self.settings['webhook_enable_lamps'][idx].insert(2, True)
                
            for i,l in enumerate(('puc', 'uc', 'exh', 'hard', 'clear', 'failed')):
                self.window[f'webhook_enable_{l}'].update(self.settings['webhook_enable_lamps'][idx][i])

    def set_webhook_ui_default(self):
        self.window['list_webhook'].update(self.settings['webhook_names'])
        self.window['webhook_names'].update('')
        self.window['webhook_urls'].update('')
        self.window['webhook_enable_pics'].update(True)
        self.window['webhook_playlist'].update(False)
        for i in range(1,14):
            self.window[f'webhook_enable_lv{i}'].update(False)
        for i in range(14,21):
            self.window[f'webhook_enable_lv{i}'].update(True)
        for l in ('puc', 'uc', 'exh', 'hard', 'clear'):
            self.window[f'webhook_enable_{l}'].update(True)
        self.window[f'webhook_enable_failed'].update(False)
        
        
    def format_score(self, score, bold:bool=True):
        if bold :
            return '**'+str(score)[0:len(str(score))-4]+"**,"+str(score)[len(str(score))-4:]
        else :
            return str(score)[0:len(str(score))-4]+","+str(score)[len(str(score))-4:]
        
    def send_custom_webhook(self, playdata:OnePlayData):
        """カスタムWebhookへの送出を行う

        Args:
            playdata (OnePlayData): 送るリザルトのデータ
        """
        diff_table = ['nov', 'adv', 'exh', 'APPEND']
        lamp_table = ['puc', 'uc', 'exh', 'hard', 'clear', 'failed', '']
        lamp_idx = lamp_table.index(playdata.lamp)
        lv = '??'
        if playdata.title in self.sdvx_logger.titles.keys():
            lv     = self.sdvx_logger.titles[playdata.title][3+diff_table.index(playdata.difficulty)]
        img_bytes = io.BytesIO()
        self.img_rot.save(img_bytes, format='PNG')
        for i in range(len(self.settings['webhook_names'])):
            # 送出判定
            sendflg = True
            ## lv
            if type(lv) == int: # レベル単位の送出フラグを見る
                sendflg &= self.settings[f'webhook_enable_lvs'][i][lv-1]
            ## ランプ
            sendflg &= self.settings[f"webhook_enable_lamps"][i][lamp_idx]

            if not sendflg: # 送出条件を満たしていなければ飛ばす
                continue
            
            webhook = DiscordWebhook(url=self.settings['webhook_urls'][i], username=f"{self.settings['webhook_player_name']}")
            # 画像送信有効時のみ添付する
            if self.settings['webhook_enable_pics'][i]:
                webhook.add_file(file=img_bytes.getvalue(), filename=f'{playdata.date}.png')
            msg = f'**{playdata.title}** ({playdata.difficulty}, Lv{lv}),   '
            msg += f'{self.format_score(playdata.cur_score)} ({"+" if playdata.cur_score > playdata.pre_score else ""}{self.format_score(playdata.cur_score - playdata.pre_score,False)}),   '
            msg += f'{playdata.lamp},   '
            webhook.content=msg
            try:
                res = webhook.execute()
            except Exception:
                self.logToWindow('webhook送出エラー(URLがおかしい？)')
                logger.debug(traceback.format_exc())

    def import_score_on_select_with_dialog(self):
        """ボタンを押したときだけ選曲画面から自己べを取り込む。合ってるかどうかの確認もやる。
        """
        self.window.write_event_value('-import_score_on_select-', " ")

    def detect(self):
        """認識処理を行う。無限ループになっており、メインスレッドから別スレッドで起動される。

        Returns:
            bool: エラー時にFalse
        """
        if self.obs == False:
            logger.debug('cannot connect to OBS -> exit')
            return False
        if self.settings['obs_source'] == '':
            self.logToWindow(self.i18n('message.main.noSource'))
            self.window['txt_obswarning'].update(self.i18n('message.main.noSource.error'))
            return False
        obsv = self.obs.ws.get_version()
        if obsv != None:
            logger.debug(f'OBSver:{obsv.obs_version}, RPCver:{obsv.rpc_version}, OBSWSver:{obsv.obs_web_socket_version}')
        done_thissong = False # 曲決定画面の抽出が重いため1曲あたり一度しか行わないように制御
        self.obs.change_text(self.settings['obs_txt_playtime'], self.settings['obs_txt_playtime_header']+str(self.playtime).split('.')[0])
        while True:
            self.get_capture_after_rotate()
            pre_mode = self.detect_mode
            if self.rta_mode:
                current = self.rta_endtime if self.rta_finished else datetime.datetime.now()
                rta_time = (current - self.rta_starttime)
                self.obs.change_text('sdvx_helper_rta_timer', str(rta_time).split('.')[0])
            # 全モード共通の処理
            if self.is_onlogo():
                self.detect_mode = detect_mode.init
            elif self.is_onresult(): # 
                self.detect_mode = detect_mode.result
            elif self.is_onselect():
                self.detect_mode = detect_mode.select
                
                
            #if self.detect_mode != detect_mode.result:
                # Reset result_vf_saved so that a new screenshot can be taken when reaching the result
                #self.result_vf_saved = False

            # モードごとの専用処理
            if self.detect_mode == detect_mode.play:
                playtime = self.playtime + (datetime.datetime.now() - self.last_play0_time)
                self.obs.change_text(self.settings['obs_txt_playtime'], self.settings['obs_txt_playtime_header']+str(playtime).split('.')[0])
                if not self.is_onplay():
                    self.detect_mode = detect_mode.init
            if self.detect_mode == detect_mode.result:
                if self.is_onresult():
                    self.save_playerinfo()
            if self.detect_mode == detect_mode.select:
                title, diff_hash, diff = self.gen_summary.ocr_only_jacket(
                    self.img_rot.crop(self.get_detect_points('select_jacket')),
                    self.img_rot.crop(self.get_detect_points('select_nov')),
                    self.img_rot.crop(self.get_detect_points('select_adv')),
                    self.img_rot.crop(self.get_detect_points('select_exh')),
                    self.img_rot.crop(self.get_detect_points('select_APPEND')),
                )
                # 選曲画面から自己べを取り込む
                if self.settings['import_from_select']:
                    sc,lamp,is_arcade = self.gen_summary.get_score_on_select(self.img_rot)
                    import_ok = True
                    if is_arcade and (not self.settings['import_arcade_score']):
                        import_ok = False
                    if import_ok:
                        now = datetime.datetime.now()
                        self.last_autosave_time = now
                        fmtnow = format(now, "%Y%m%d_%H%M%S")
                        best_sc = 0
                        best_lamp = 'failed'
                        lamp_table = ['puc', 'uc', 'exh', 'hard', 'clear', 'failed']
                        for d in self.sdvx_logger.best_allfumen:
                            if (d.title == title) and (d.difficulty.lower() == diff.lower()):
                                best_sc = d.best_score
                                best_lamp = d.best_lamp
                        # 本ツール内のbestと合っていない場合(取り込み漏れorエラー動作)は選曲画面のスコアを登録
                        #if (sc!=best_sc) or (lamp_table.index(lamp) != lamp_table.index(best_lamp)):
                        if sc <= 10000000:
                            if (sc>best_sc) or (lamp_table.index(lamp) < lamp_table.index(best_lamp)):
                                self.logToWindow(f"{self.i18n('message.main.personalBest')}\n-> {title}({diff.upper()}): {sc:,}, {lamp}")
                                self.sdvx_logger.push(title, sc, 0, lamp, diff, fmtnow)
                                if self.rta_mode:
                                    self.rta_logger.push(title, sc, 0, lamp, diff, fmtnow)
                                self.check_rival_update() # お手紙ビューを更新
                if diff_hash < 8:
                    self.sdvx_logger.update_rival_view(title, diff)
                    self.sdvx_logger.gen_vf_onselect(title, diff)
                    self.sdvx_logger.gen_history_cursong(title, diff)
                if not self.is_onselect():
                    self.detect_mode = detect_mode.init
            if self.detect_mode == detect_mode.init:
                if not done_thissong:
                    if self.is_ondetect():
                        #self.logToWindow(f"{self.i18n('message.on.detect')}")
                        time.sleep(self.params['detect_wait'])
                        self.get_capture_after_rotate()
                        self.gen_summary.update_musicinfo(self.img_rot)
                        self.obs.refresh_source('nowplaying.html')
                        self.obs.refresh_source('nowplaying')
                        # ライバル欄更新のため、曲決定画面からもOCRを動かしておく
                        title, diff_hash, diff = self.gen_summary.ocr_from_detect()
                        self.sdvx_logger.update_rival_view(title, diff)
                        self.sdvx_logger.gen_vf_onselect(title, diff)
                        self.sdvx_logger.gen_history_cursong(title, diff)
                        done_thissong = True
                #if self.is_onplay() and done_thissong: # 曲決定画面を検出してから入る(曲終了時に何度も入らないように)
                if self.is_onplay():
                    # Reset result_vf_saved so that a new screenshot can be taken when reaching the result
                    #self.result_vf_saved = False
                    now = datetime.datetime.now()
                    time_delta = (now - self.last_play1_time).total_seconds()
                    #logger.debug(f'diff = {diff}s')
                    if time_delta > self.settings['play0_interval']: # 曲終わりのアニメーション後に再度入らないようにする
                        self.detect_mode = detect_mode.play

            # 状態遷移判定
            if pre_mode != self.detect_mode:
                if self.detect_mode == detect_mode.play:
                    self.last_play0_time = datetime.datetime.now()
                    self.control_obs_sources('play0')
                    self.plays += 1
                    self.window['txt_plays'].update(str(self.plays))
                    plays_str = f"{self.settings['obs_txt_plays_header']}{self.plays}{self.settings['obs_txt_plays_footer']}"
                    self.obs.change_text(self.settings['obs_txt_plays'], plays_str)
                    done_thissong = False # 曲が始まるタイミングでクリア
                if self.detect_mode == detect_mode.result:
                    self.control_obs_sources('result0')
                    time.sleep(float(self.settings['autosave_prewait']))
                    if self.settings['autosave_always']:
                        now = datetime.datetime.now()
                        diff = (now - self.last_autosave_time).total_seconds()
                        logger.debug(f'diff = {diff}s')
                        if diff > self.settings['autosave_interval']: # VF演出の前後で繰り返さないようにする
                            self.save_screenshot_general()
                            self.sdvx_logger.gen_sdvx_battle()
                            self.sdvx_logger.save_alllog()
                if self.detect_mode == detect_mode.select:
                    self.control_obs_sources('select0')
                    if self.chk_blastermax():
                        self.obs.change_text(self.settings['obs_txt_blastermax'],'BLASTER GAUGEが最大です!!　　　　　　　　　　　　')
                        if self.settings['alert_blastermax']:
                            self.play_wav('resources/blastermax.wav')
                    else:
                        self.obs.change_text(self.settings['obs_txt_blastermax'],'')

                if pre_mode == detect_mode.play:
                    self.last_play1_time = datetime.datetime.now()
                    self.playtime += (self.last_play1_time - self.last_play0_time)
                    self.obs.change_text(self.settings['obs_txt_playtime'], self.settings['obs_txt_playtime_header']+str(self.playtime).split('.')[0])
                    self.control_obs_sources('play1')
                if pre_mode == detect_mode.result:
                    self.control_obs_sources('result1')
                if pre_mode == detect_mode.select:
                    self.control_obs_sources('select1')

            if self.stop_thread:
                break
            time.sleep(0.1)
        logger.debug(f'detect end!')

    def main(self):
        """メイン処理。PySimpleGUIのイベント処理など。
        """
        logger.debug('started')
        now = datetime.datetime.now()
        now_mod = now - datetime.timedelta(hours=self.settings['logpic_offset_time']) # 多少の猶予をつける。2時間前までは遡る

        self.gen_summary = GenSummary(now_mod)
        self.gen_summary.generate()
        self.starttime = now
        self.gui_main()
        self.logToWindow(f"{self.i18n('message.results.outputdir', self.settings['autosave_dir'])}")
        if self.settings['get_rival_score']:
            try:
                self.sdvx_logger.get_rival_score(self.settings['player_name'], self.settings['rival_names'], self.settings['rival_googledrive'])
                self.logToWindow(self.i18n('message.rivals.data.completed'))
            except Exception: # 関数全体が落ちる=Googleドライブへのアクセスでコケたときの対策
                logger.debug(traceback.format_exc())
                self.logToWindow(self.i18n('message.rivals.data.failed')) # ネットワーク接続やURL設定を見直す必要がある
        self.load_rivallog()
        self.check_rival_update()
        self.th = False
        if type(self.obs) == OBSSocket:
            self.obs.set_scene_collection(self.settings['obs_scene_collection'])
        self.control_obs_sources('boot')
        plays_str = f"{self.settings['obs_txt_plays_header']}{self.plays}{self.settings['obs_txt_plays_footer']}"
        if self.obs != False:
            self.obs.change_text(self.settings['obs_txt_plays'], plays_str)
        self.start_detect()

        if self.settings['auto_update']:
            self.window.write_event_value('アップデートを確認', " ")

        while True:
            ev, val = self.window.read()
            #logger.debug(f"ev:{ev}")
            self.update_settings(ev, val)
            if ev in (sg.WIN_CLOSED, 'Escape:27', '-WINDOW CLOSE ATTEMPTED-', 'btn_close_info', 'btn_close_setting'):
                if self.gui_mode == gui_mode.main: # メインウィンドウを閉じた場合
                    self.save_settings()
                    # maya2serverへのアップロード
                    self.sdvx_logger.upload_best(volforce=self.vf_cur)
                    self.control_obs_sources('quit')
                    summary_filename = f"{self.settings['autosave_dir']}/{self.starttime.strftime('%Y%m%d')}_summary.png"
                    self.logToWindow(f"{self.i18n('message.main.savingResults')}\n==> {summary_filename}")
                    self.gen_summary.generate_today_all(summary_filename)
                    self.sdvx_logger.save_alllog()
                    self.sdvx_logger.gen_playcount_csv(self.settings['my_googledrive']+'/playcount.csv')
                    self.update_mybest()
                    self.save_rivallog()
                    self.send_playlist()
                    self.logToWindow(f'{self.i18n("message.main.playLogSaved")}')
                    vf_filename = f"{self.settings['autosave_dir']}/{self.starttime.strftime('%Y%m%d')}_total_vf.png"
                    
                    if self.settings['enable_discord_presence'] :
                        self.presence.destroy()
                    #print(f"VF対象一覧を保存中 (OBSに設定していれば保存されます) ...\n==> {vf_filename}")
                    try:
                        tmps, tmpid = self.obs.search_itemid(self.settings[f'obs_scene_select'], 'sdvx_stats.html')
                        if self.obs.enable_source(tmps, tmpid):
                            time.sleep(2)
                            self.obs.ws.save_source_screenshot('sdvx_stats.html', 'png', vf_filename, 3000, 2300, 100)
                            self.logToWindow(self.i18n('message.main.savingVF'))
                            self.obs.disable_source(tmps, tmpid)
                    except Exception:
                        pass
                    try:
                        tmps, tmpid = self.obs.search_itemid(self.settings[f'obs_scene_select'], 'sdvx_stats_v2.html')
                        if self.obs.enable_source(tmps, tmpid):
                            time.sleep(2)
                            self.obs.ws.save_source_screenshot('sdvx_stats_v2.html', 'png', vf_filename, 3500, 2700, 100)
                            self.logToWindow(self.i18n('message.main.savingVF'))
                            self.obs.disable_source(tmps, tmpid)
                    except Exception:
                        pass
                    if self.rta_mode:
                        try:
                            tmps, tmpid = self.obs.search_itemid(self.settings[f'obs_scene_select'], 'rta_sdvx_stats_v2.html')
                            if self.obs.enable_source(tmps, tmpid):
                                time.sleep(2)
                                rta_filename = f"{self.settings['autosave_dir']}/{self.starttime.strftime('%Y%m%d')}_rta_result.png"
                                self.obs.ws.save_source_screenshot('rta_sdvx_stats_v2.html', 'png', rta_filename, 3500, 2700, 100)
                                self.logToWindow(self.i18n('message.main.savingRTA'))
                                self.obs.disable_source(tmps, tmpid)
                        except Exception:
                            pass
                    break
                else: # メイン以外のGUIを閉じた場合
                    self.start_detect()
                    try:
                        plays_str = f"{self.settings['obs_txt_plays_header']}{self.plays}{self.settings['obs_txt_plays_footer']}"
                        if self.obs != False:
                            self.obs.change_text(self.settings['obs_txt_plays'], plays_str)
                        self.gui_main()
                    except Exception as e:
                        print(traceback.format_exc())
            
            elif ev == self.i18n('menu.file.obs'):
                self.stop_detect()
                if self.connect_obs():
                    self.gui_obs_control()
                else:
                    sg.popup_error(self.i18n('popup.obsFail'))
            elif ev == self.i18n('menu.rta.start'):
                self.start_rta_mode()
            elif ev == 'btn_savefig':
                self.save_screenshot_general()
            
            elif ev == 'btn_save_vf':
                self.capture_volforce_btn()
                
            elif ev == 'btn_save_summary':
                self.capture_summary_btn()
                
            elif ev == 'combo_scene': # シーン選択時にソース一覧を更新
                if self.obs != False:
                    sources = self.obs.get_sources(val['combo_scene'])
                    self.window['combo_source'].update(values=sources)
            elif ev == 'set_obs_source':
                tmp = val['combo_source'].strip()
                if tmp != "":
                    self.settings['obs_source'] = tmp
                    self.window['obs_source'].update(tmp)
            elif ev.startswith('set_scene_'): # 各画面のシーンsetボタン押下時
                tmp = val['combo_scene'].strip()
                self.settings[ev.replace('set_scene', 'obs_scene')] = tmp
                self.window[ev.replace('set_scene', 'obs_scene')].update(tmp)
            elif ev.startswith('add_enable_') or ev.startswith('add_disable_'):
                tmp = val['combo_source'].strip()
                key = ev.replace('add', 'obs')
                if tmp != "":
                    if tmp not in self.settings[key]:
                        self.settings[key].append(tmp)
                        self.window[key].update(self.settings[key])
            elif ev.startswith('del_enable_') or ev.startswith('del_disable_'):
                key = ev.replace('del', 'obs')
                if len(val[key]) > 0:
                    tmp = val[key][0]
                    if tmp != "":
                        if tmp in self.settings[key]:
                            self.settings[key].pop(self.settings[key].index(tmp))
                            self.window[key].update(self.settings[key])
            elif ev == 'scene_collection': # シーンコレクションを選択
                self.settings['obs_scene_collection'] = val[ev]
                self.obs.set_scene_collection(val[ev]) # そのシーンコレクションに切り替え
                time.sleep(3)
                obs_scenes = []
                tmp = self.obs.get_scenes()
                tmp.reverse()
                for s in tmp:
                    obs_scenes.append(s['sceneName'])
                self.window['combo_scene'].update(values=obs_scenes) # シーン一覧を更新
            elif ev == 'btn_autosave_dir':
                tmp = filedialog.askdirectory()
                if tmp != '':
                    self.settings['autosave_dir'] = tmp
                    self.window['txt_autosave_dir'].update(tmp)
            elif ev == 'btn_my_googledrive':
                tmp = filedialog.askdirectory()
                if tmp != '':
                    self.settings['my_googledrive'] = tmp
                    self.window['txt_my_googledrive'].update(tmp)

            elif ev == self.i18n('menu.file.updates'):
                ver = sdvx_utils.get_latest_version()
                if sdvx_utils.compare_version(ver,SWVER) == -1 :
                    self.logToWindow(f'{self.i18n("message.main.currentVersion")}: {SWVER}, {self.i18n("message.main.latestVersion")}: {ver}')
                    ans = sg.popup_yes_no(f'{self.i18n("popup.updateFound")} \n\n{SWVER} -> {ver}\n\n{self.i18n("popup.closeApp")}', icon=self.ico)
                    if ans == "Yes":
                        self.save_settings()
                        self.control_obs_sources('quit')
                        if os.path.exists('update.exe'):
                            logger.info('アップデート確認のため終了します')
                            res = subprocess.Popen('update.exe')
                            break
                        else:
                            sg.popup_error(self.i18n('popup.updateMissing'), icon=self.ico)
                else:
                    self.logToWindow(f'{self.i18n("message.version")} ({SWVER})')

            elif ev in ('btn_setting', self.i18n('menu.file.settings')):
                self.stop_detect()
                self.gui_setting()
            elif ev == 'read_from_result':
                self.sdvx_logger.import_from_resultimg()
            elif ev == 'gen_jacket_imgs':
                self.sdvx_logger.gen_jacket_imgs()
            ### webhook関連
            elif ev == self.i18n('menu.file.webhook'):
                self.stop_detect()
                self.gui_webhook()
            elif ev == self.i18n('menu.rivals.google'):
                self.stop_detect()
                self.gui_googledrive()
            elif ev == self.i18n('menu.rivals.get'):
                self.update_rival()
            elif ev == 'webhook_add':
                self.webhook_add(val)
            elif ev == 'webhook_del':
                self.webhook_del(val)
            elif ev == 'list_webhook':
                self.webhook_read(val)
            elif ev == 'webhook_enable_alllv':
                for i in range(1,21):
                    self.window[f"webhook_enable_lv{i}"].update(val[ev])
            elif ev == 'webhook_enable_alllamp':
                for l in ('puc', 'uc', 'exh', 'hard', 'clear', 'failed'):
                    self.window[f"webhook_enable_{l}"].update(val[ev])

            ### Googleドライブ関連
            elif ev == 'add_rival':
                name = val['rival_name']
                url  = val['rival_googledrive']
                url_split = url.split('/')
                # https://drive.google.com/open?id=1VWSUs7DRBWBiKK2zmIyTknQiUugC6sVK&usp=drive_fs 
                # https://drive.google.com/file/d/10EeiBpPZCHBDTkeLfyZSB7rE_2ALIwBm/view
                if (len(url_split) == 7) and (len(url_split[6]) == 33) and (url_split[2]=='drive.google.com'):
                    url = url_split[6]
                elif (len(url_split) == 4): # エクスプローラでコピーした場合のURL
                    url = url_split[-1].split('=')[1].split('&')[0]
                logger.debug(f"name={name}, url={url}")
                if name != '' and url != '' and len(url) == 33:
                    self.settings['rival_names'].append(name)
                    self.settings['rival_googledrive'].append(url)
                    self.window['rival_name'].update('')
                    self.window['rival_googledrive'].update('')
                self.window['rival_names'].update([[self.settings['rival_names'][i], self.settings['rival_googledrive'][i]] for i in range(len(self.settings['rival_names']))])
            elif ev == 'del_rival':
                for idx in val['rival_names']:
                    self.settings['rival_names'].pop(idx)
                    self.settings['rival_googledrive'].pop(idx)
                self.window['rival_names'].update([[self.settings['rival_names'][i], self.settings['rival_googledrive'][i]] for i in range(len(self.settings['rival_names']))])
            elif ev == 'open_rival':
                for idx in val['rival_names']:
                    id = self.settings['rival_googledrive'][idx]
                    webbrowser.open(f"https://drive.google.com/file/d/{id}/view")

            ### ツイート機能
            elif ev == self.i18n('menu.analysis.tweet'):
                msg = self.sdvx_logger.analyze()
                encoded_msg = urllib.parse.quote(f"{msg}")
                webbrowser.open(f"https://twitter.com/intent/tweet?text={encoded_msg}")
            elif ev == self.i18n('menu.analysis.csv'):
                tmp = filedialog.asksaveasfilename(defaultextension='csv', filetypes=[("csv file", "*.csv")], initialdir='./', initialfile='sdvx_helper_alllog.csv')
                if tmp != '':
                    ret = self.sdvx_logger.gen_alllog_csv(tmp)
                    if ret:
                        sg.popup_ok(f'{self.i18n("popup.csvOutput.success")}\n\n(-> {tmp})')
                    else:
                        sg.popup_error(self.i18n('popup.csvOutput.fail'))
            elif ev == self.i18n('menu.analysis.csvBest'):
                tmp = filedialog.asksaveasfilename(defaultextension='csv', filetypes=[("csv file", "*.csv")], initialdir='./', initialfile='sdvx_helper_best.csv')
                if tmp != '':
                    ret = self.sdvx_logger.gen_best_csv(tmp)
                    if ret:
                        sg.popup_ok(f'{self.i18n("popup.csvOutput.success")}\n\n(-> {tmp})')
                    else:
                        sg.popup_error(self.i18n('popup.csvOutput.fail'))
            elif ev == '-import_score_on_select-':
                if self.detect_mode == detect_mode.select:
                    title, diff_hash, diff = self.gen_summary.ocr_only_jacket(
                        self.img_rot.crop(self.get_detect_points('select_jacket')),
                        self.img_rot.crop(self.get_detect_points('select_nov')),
                        self.img_rot.crop(self.get_detect_points('select_adv')),
                        self.img_rot.crop(self.get_detect_points('select_exh')),
                        self.img_rot.crop(self.get_detect_points('select_APPEND')),
                    )
                    sc,lamp,is_arcade = self.gen_summary.get_score_on_select(self.img_rot)
                    now = datetime.datetime.now()
                    self.last_autosave_time = now
                    fmtnow = format(now, "%Y%m%d_%H%M%S")
                    if sc <= 10000000:
                        ans = sg.popup_yes_no(f'{self.i18n("popup.personalBest.register")}\n{self.i18n("popup.personalBest.title")}:{title} ({diff})\n{self.i18n("popup.personalBest.score")}:{sc}, {self.i18n("popup.personalBest.lamp")}:{lamp}, {self.i18n("popup.personalBest.arcadeScore")}:{is_arcade}', icon=self.ico)
                        if ans == "Yes":
                            self.logToWindow(f"{self.i18n('message.main.personalBest')}\n-> {title}({diff.upper()}): {sc:,}, {lamp}")
                            self.sdvx_logger.push(title, sc, 0, lamp, diff, fmtnow)
                            if self.rta_mode:
                                self.rta_logger.push(title, sc, 0, lamp, diff, fmtnow)
                            self.check_rival_update() # お手紙ビューを更新
                    else:
                        self.logToWindow(f'{self.i18n("message.main.error.aquisitionFailed")}({title},{diff},{sc},{lamp})')
                else:
                    self.logToWindow(self.i18n('message.main.error.noSongSelection'))                    
            elif ev == 'locale':
                self.bundle = PoorManResourceBundle(val['locale'].lower())
                self.default_locale = val['locale']
                self.i18n = self.bundle.get_text
                self.window.close()
                self.gui_main()
            elif ev == 'btn_exit':
                self.window.close();

if __name__ == '__main__':
    a = SDVXHelper()
    a.main()
