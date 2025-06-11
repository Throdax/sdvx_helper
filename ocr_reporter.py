# OCR未検出結果の報告用GUI
# 曲一覧を持っておき、各hash値に曲名情報を入れてpickleに追加する
# pickleをwebhookで送信する

# bemaniwikiから全曲情報を取得
# 自動保存フォルダの画像を確認し、認識できないものを一通り抽出
# リストビュー+選択したファイルについてジャケット、曲名を出すビュー
# 
import PySimpleGUI as sg
from bs4 import BeautifulSoup
import sys
import requests
import pickle
import threading
from collections import defaultdict
from gen_summary import *
from manage_settings import *
from params_secret import *
import traceback
import urllib
import logging, logging.handlers
from tkinter import filedialog
import re
from poor_man_resource_bundle import *
import concurrent.futures
#from datetime import * 


SETTING_FILE = 'settings.json'
sg.theme('SystemDefault')
diff_table = ['nov', 'adv', 'exh', 'APPEND']

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

class Reporter:
    
    color_lock = threading.Lock()
    ocr_found = 0
    ocr_not_found = 0
    ocr_processed = 0
    
    def __init__(self, chk_update:bool=True):
        self.default_locale = 'EN'
        self.bundle = PoorManResourceBundle(self.default_locale)
        self.i18n = self.bundle.get_text
        
        self.start = datetime.datetime.now()
        self.load_settings()
        if chk_update:
            self.update_musiclist()
        #self.gen_summary = GenSummary(start,autosave_dir='D:/Tools/SoundVoltex/results')
        self.gen_summary = GenSummary(self.start)
        self.load_musiclist()
        self.read_bemaniwiki()
        self.ico=self.ico_path('icon.ico')
        self.num_added_fumen = 0 # 登録した譜面数
        self.flg_registered = {} # key:ファイル名、値:登録済みならTrue.do_coloringの結果保存用。
        self.gui()
        self.main()

    def ico_path(self, relative_path):
        try:
            base_path = sys._MEIPASS
        except Exception:
            base_path = os.path.abspath(".")
        return os.path.join(base_path, relative_path)

    # 曲リストを最新化
    def update_musiclist(self):
        try:
            with urllib.request.urlopen(self.params['url_musiclist']) as wf:
                with open('resources/musiclist.pkl', 'wb') as f:
                    f.write(wf.read())
            logger.debug('musiclist.pklを更新しました。')
        except Exception:
            logger.debug(traceback.format_exc())

    def load_settings(self):
        ret = {}
        try:
            with open(SETTING_FILE) as f:
                ret = json.load(f)
                logger.debug(f"設定をロードしました。\n")
        except Exception as e:
            logger.debug(traceback.format_exc())
            logger.debug(f"有効な設定ファイルなし。デフォルト値を使います。")

        ### 後から追加した値がない場合にもここでケア
        for k in default_val.keys():
            if not k in ret.keys():
                logger.debug(f"{k}が設定ファイル内に存在しません。デフォルト値({default_val[k]}を登録します。)")
                ret[k] = default_val[k]
        self.settings = ret
        with open(self.settings['params_json'], 'r') as f:
            self.params = json.load(f)
        return ret

    def load_musiclist(self):
        try:
            with open('resources/musiclist.pkl', 'rb') as f:
                self.musiclist = pickle.load(f)
            print(f"{self.i18n('log.song.db')}: {len(self.musiclist['jacket']['exh'])}")
            print(f'{self.i18n("log.song.wiki")}: {len(self.musiclist["titles"].keys())}')
        except:
            logger.debug('musiclist読み込み時エラー。新規作成します。')
            self.musiclist = {}
            self.musiclist['jacket'] = {}
            self.musiclist['jacket']['nov'] = {}
            self.musiclist['jacket']['adv'] = {}
            self.musiclist['jacket']['exh'] = {}
            self.musiclist['jacket']['APPEND'] = {}
            self.musiclist['info'] = {}
            self.musiclist['info']['nov'] = {}
            self.musiclist['info']['adv'] = {}
            self.musiclist['info']['exh'] = {}
            self.musiclist['info']['APPEND'] = {}
        if not 'titles' in self.musiclist.keys():
            print('各曲のレベル情報がないので新規作成します。')
            self.musiclist['titles'] = {}

    def merge_musiclist(self):
        filename = filedialog.askopenfilename()
        try:
            with open(filename, 'rb') as f:
                tmp = pickle.load(f)
            pre_len = len(self.musiclist['jacket']['exh'].keys())
            for pos in ('jacket', 'info'):
                for diff in ('nov', 'adv', 'exh', 'APPEND'):
                    for s in tmp[pos][diff].keys(): # 曲名のリスト
                        if s not in self.musiclist[pos][diff].keys():
                            self.musiclist[pos][diff][s] = tmp[pos][diff][s]
                            logger.debug(f'added! {s}({diff},{pos}): {tmp[pos][diff][s]}')
                        elif self.musiclist[pos][diff][s] != tmp[pos][diff][s]:
                            logger.debug(f'merged! {s}({diff},{pos}): {tmp[pos][diff][s]} (before:{self.musiclist[pos][diff][s]})')
                            self.musiclist[pos][diff][s] = tmp[pos][diff][s]
            cur_len = len(self.musiclist['jacket']['exh'].keys())
            logger.debug(f'マージ完了。{pre_len:,} -> {cur_len:,}')
            print(f'マージ完了。{pre_len:,} -> {cur_len:,}')
        except Exception:
            logger.debug(traceback.format_exc())

    def save(self):
        with open('resources/musiclist.pkl', 'wb') as f:
            pickle.dump(self.musiclist, f)

    def read_bemaniwiki(self):
        req = requests.get('https://bemaniwiki.com/index.php?%A5%B3%A5%CA%A5%B9%A5%C6/SOUND+VOLTEX+EXCEED+GEAR/%B3%DA%B6%CA%A5%EA%A5%B9%A5%C8')

        stop_string = '[STOP]'

        soup = BeautifulSoup(req.text, 'html.parser')
        titles = self.musiclist['titles']
        for tr in soup.find_all('tr'):
            tds = tr.find_all('td')
            numtd = len(tds)
            if numtd in (7,8):
                if tds[2].text != 'BPM':
                    tmp = [tds[0].text, tds[1].text, tds[2].text]
                    
                    nov_text = tds[3].text
                    if nov_text.startswith(stop_string) :
                        nov_text = nov_text[len(stop_string)].strip()
                    tmp.append(int(nov_text))
                    
                    adv_text = tds[4].text
                    if adv_text.startswith(stop_string) :
                        adv_text = adv_text[len(stop_string)].strip()
                    tmp.append(int(adv_text))
                                        
                    exh_text = tds[5].text
                    if exh_text.startswith(stop_string) :
                        exh_text = exh_text[len(stop_string)].strip()
                    tmp.append(int(exh_text))
                                        
                    if tds[6].text not in ('', '-'):
                        append_text = tds[6].text
                        if append_text.startswith(stop_string) :
                            append_text = append_text[len(stop_string)].strip()
                        tmp.append(int(append_text))
                    else:
                        tmp.append(None)
                    titles[tds[0].text] = tmp

        urls = [
            'https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%B5%EC%B6%CA%A5%EA%A5%B9%A5%C8',
            'https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%BF%B7%B6%CA%A5%EA%A5%B9%A5%C8'
        ]
        # AC版のwikiを読む
        for url in urls:
            req = requests.get(url)
            soup = BeautifulSoup(req.text, 'html.parser')
            # rowspanのカウンタ。日付分は正規表現で見るので不要
            cnt_rowspan_artist = 0
            cnt_rowspan_bpm    = 0
            pre_artist = ''
            pre_bpm    = ''
            for tr in soup.find_all('tr'):
                tds = tr.find_all('td')
                numtd = len(tds)
                title_flg = 0
                rowspan_flg = 0
                if re.search('\\d{4}/\\d{2}/\\d{2}', tds[0].text):
                    title_flg = 1 # タイトル行がどっちかのみ、これ以降ずらさない
                    rowspan_flg = 1 # 難易度の先頭ポインタ
                if numtd in (7+rowspan_flg,8+rowspan_flg):
                    if tds[3].text != 'BPM':
                        title  = tds[0+title_flg].text
                        artist = tds[1+title_flg].text
                        bpm    = tds[2+title_flg].text
                        if re.search('\\A\\d{4}/\\d{2}/\\d{2}\\Z', tds[0].text):
                            title = tds[1].text
                        #print(tds)
                        if 'rowspan' in tds[1+title_flg].attrs.keys(): # rowspanありの行はずらさない
                            cnt_rowspan_artist = int(tds[1+title_flg].attrs['rowspan'])
                            pre_artist = tds[1+title_flg].text
                        elif cnt_rowspan_artist > 0: # rowspanの次の行以降はカウンタが正なら1ずらす
                            rowspan_flg -= 1
                            artist = pre_artist
                            bpm    = tds[1+title_flg].text
                        if 'rowspan' in tds[2+title_flg].attrs.keys():
                            cnt_rowspan_bpm = int(tds[2+title_flg].attrs['rowspan'])
                            pre_bpm = tds[2+title_flg].text
                        elif cnt_rowspan_bpm > 0:
                            rowspan_flg -= 1
                            bpm = pre_bpm
                        if tds[3+rowspan_flg].text != '-': # ごりらがいるんだ等、1つ上と曲違いのやつ
                            tmp = [title, artist, bpm]
                            tmp.append(int(re.findall('\\d+', tds[3+rowspan_flg].text)[-1]))
                            tmp.append(int(re.findall('\\d+', tds[4+rowspan_flg].text)[-1]))
                            tmp.append(int(re.findall('\\d+', tds[5+rowspan_flg].text)[-1]))
                            if tds[6+rowspan_flg].text not in ('', '-'):
                                tmp.append(int(re.findall('\\d+', tds[6+rowspan_flg].text)[-1]))
                            else:
                                tmp.append(None)
                            if title not in titles:
                                titles[title] = tmp
                        else:
                            tmp[-1] = int(re.findall('\\d+', tds[6+rowspan_flg].text)[-1])
                            titles[title] = tmp
                        #if ('Spear of Justice' in tds[0].text) or ('ASGORE' in tds[1].text):
                        #    print([tds[i].text for i in range(len(tds))])
                        #    print(f"tflg:{title_flg}, rflg:{rowspan_flg}, title:{title}, artist:{artist}(pre:{pre_artist}), bpm:{bpm}(pre:{pre_bpm})")
                        #    print()
                cnt_rowspan_artist = max(0, cnt_rowspan_artist - 1)
                cnt_rowspan_bpm    = max(0, cnt_rowspan_bpm - 1)

        self.titles = titles
        self.musiclist['titles'] = titles
        print(f"read_bemaniwiki end. (total {len(titles):,} songs)")

    def send_webhook(self, title, difficulty, hash_jacket, hash_info):
        try:
            webhook = DiscordWebhook(url=url_webhook_reg, username="unknown title info")
            msg = f"{self.i18n('webhook.ocr.title')}: **{title}**\n"
            msg += f" - {self.i18n('webhook.ocr.hash.jacket')}: **{hash_jacket}**"
            if hash_info != "":
                msg += f" - {self.i18n('webhook.ocr.hash.info')}: **{hash_info}**"
            if self.gen_summary.result_parts != False:
                img_bytes = io.BytesIO()
                self.gen_summary.result_parts['info'].crop((0,0,260,65)).save(img_bytes, format='PNG')
                webhook.add_file(file=img_bytes.getvalue(), filename=f'info.png')
                img_bytes = io.BytesIO()
                self.gen_summary.result_parts['difficulty'].save(img_bytes, format='PNG')
                webhook.add_file(file=img_bytes.getvalue(), filename=f'difficulty.png')
            msg += f"({self.i18n('webhook.ocr.difficulty')}: **{difficulty.upper()}**)"
            webhook.content=msg
            res = webhook.execute()
        except Exception:
            print(traceback.format_exc())

    def send_pkl(self):
        webhook = DiscordWebhook(url=url_webhook_reg, username="unknown title info")
        with open('resources/musiclist.pkl', 'rb') as f:
            webhook.add_file(file=f.read(), filename='musiclist.pkl')
        webhook.content = f"{self.i18n('webhook.number.added.scores')}: {self.num_added_fumen}, total: {len(self.musiclist['jacket']['APPEND'])+len(self.musiclist['jacket']['nov'])+len(self.musiclist['jacket']['adv'])+len(self.musiclist['jacket']['exh'])}"
        webhook.content += f", {self.i18n('webhook.number.added.songs')}: {len(self.musiclist['jacket']['APPEND'])}"
        res = webhook.execute()
    
    ##############################################
    ##########          GUIの設定
    ##############################################
    def gui(self, refresh:bool=False):
        header = ['title', 'artist', 'bpm', 'nov', 'adv', 'exh', '(APPEND)']
        layout_info = [
            [sg.Image(None, size=(137,29), key='difficulty')],
            [sg.Image(None, size=(526,64), key='info')],
        ]
        layout_tables = [
            [sg.Table(
                []
                ,headings=header
                ,auto_size_columns=False
                ,col_widths=[40,40,7,3,3,3,3]
                ,alternating_row_color='#eeeeee'
                ,justification='left'
                ,key='musics'
                ,size=(120,10)
                ,enable_events=True
                ,font=(None, 16)
                )
            ],
            [sg.Table(
                []
                ,headings=['saved files']
                ,auto_size_columns=False
                ,col_widths=[90]
                ,alternating_row_color='#eeeeee'
                ,justification='left'
                ,key='files'
                ,size=(90,10)
                ,enable_events=True
                ,font=(None, 16)
                )
            ],
        ]
        layout_db = [
            [
                sg.Text('difficulty:'), sg.Combo(['', 'nov', 'adv', 'exh', 'APPEND'], default_value='exh', key='combo_diff_db', font=(None,16), enable_events=True)
                ,sg.Button(self.i18n('button.merge.pkl'), key='merge')
                ,sg.Text('0', key='num_hash'), sg.Text(self.i18n('text.songs'))
                ,sg.Button(self.i18n('button.send.pkl'), key='send_pkl')
            ],
            [
                sg.Table(
                    []
                    ,headings=['title', 'hash']
                    ,auto_size_columns=False
                    ,col_widths=[40, 20]
                    ,alternating_row_color='#eeeeee'
                    ,justification='left'
                    ,key='db'
                    ,size=(90,10)
                    ,enable_events=True
                    ,font=(None, 16)
                )
            ],
        ]
        layout = [
            [
                sg.Text('Language/言語', font=(None,16)),sg.Combo(self.bundle.get_available_bundles(), key='locale', font=(None,16), default_value=self.default_locale,enable_events=True)
            ],
            [ 
                sg.Text('search:', font=(None,16)), sg.Input('', size=(40,1), key='filter', font=(None,16), enable_events=True), sg.Button('clear', font=(None,16)), sg.Text('('+self.i18n('text.registered')+': ', font=(None,16)), sg.Text('0', key='num_added_fumen', font=(None,16)), sg.Text(self.i18n('text.music_score')+')', font=(None,16))
            ],
            [
                sg.Text('title:', font=(None,16)), sg.Input('', key='txt_title', font=(None,16), size=(50,1))
            ],
            [
                sg.Text('hash_jacket:', font=(None,16)), sg.Input('', key='hash_jacket', size=(25,1), font=(None,16)), sg.Text('hash_info:',font=(None,16)), sg.Input('', key='hash_info', size=(25,1),font=(None,16))
                ,sg.Text(self.i18n('text.difficulty')+':', font=(None,16)), sg.Combo(['', 'nov', 'adv', 'exh', 'APPEND'], key='combo_difficulty', font=(None,16))
            ],
            [sg.Button(self.i18n('button.resgister'), key='register'), sg.Button(self.i18n('button.rescan'), key='coloring')],
            [sg.Column(layout_tables, key='column_table'), sg.Column(layout_db, key='column_db')],
            [sg.Text('', text_color="#ff0000", key='state', font=(None,16))],
            [sg.Image(None, size=(100,100), key='jacket'), sg.Column(layout_info)]
        ]
        if not refresh : 
            self.window = sg.Window(f"{self.i18n('window.ocr.title')}", layout, resizable=True, grab_anywhere=True,return_keyboard_events=True,finalize=True,enable_close_attempted_event=True,icon=self.ico,location=(self.settings['lx'], self.settings['ly']), size=(900,780))
        else :
            self.window.Title = self.i18n('window.ocr.title')
             
        self.window['musics'].expand(expand_x=True, expand_y=True)
        self.window['files'].expand(expand_x=True, expand_y=True)
        self.window['column_table'].expand(expand_x=True, expand_y=True)
        self.window['db'].expand(expand_x=True, expand_y=True)
        self.window['column_db'].expand(expand_x=True, expand_y=True)
        self.window['musics'].update(self.get_musiclist())
        filelist, bgcs = self.get_filelist()
        self.window['files'].update(filelist, row_colors=bgcs)
        self.get_dblist()

    # bemaniwikiから取得した曲一覧を返す
    def get_musiclist(self):
        ret = []
        for s in self.musiclist['titles'].values():
            to_push = True
            if self.window['filter'].get().strip() != '':
                for search_word in self.window['filter'].get().strip().split(' '):
                    if (search_word.lower() not in s[0].lower()) and (search_word.lower() not in s[1].lower()):
                        to_push = False
            if to_push: # 表示するデータを追加
                ret.append(s)
        self.musiclist_gui = ret # 現在GUIに表示している曲一覧を記憶しておく
        return ret
    
    def get_dblist(self):
        dat = []
        if self.window['combo_diff_db'].get() != '':
            titles = [k for k in self.musiclist['jacket'][self.window['combo_diff_db'].get()].keys()]
            titles = sorted(titles, key=str.lower)
            for s in titles:
                to_push = True
                if self.window['filter'].get().strip() != '':
                    for search_word in self.window['filter'].get().strip().split(' '):
                        if (search_word.lower() not in s.lower()):
                            to_push = False
                if to_push: # 表示するデータを追加
                    dat.append([s,self.musiclist['jacket'][self.window['combo_diff_db'].get()][s]])
        self.window['num_hash'].update(len(dat))
        self.window['db'].update(dat)

    def get_filelist(self):
        ret = []
        bgcs = []
        for i,f in enumerate(self.gen_summary.get_result_files()):
            ret.append(f.replace('\\','/'))
            if i%2 == 0:
                bgcs.append([len(bgcs), '#000000', '#ffffff'])
            else:
                bgcs.append([len(bgcs), '#000000', '#eeeeee'])
            # is_resultチェックを全画像に対してやるのは遅いのでボツ
            #tmp = Image.open(f)
            #if self.gen_summary.is_result(tmp):
            #    ret.append(f)
        self.filelist_bgcolor = bgcs
        return ret, bgcs
    
    def apply_coloring(self):
        
        update_list = []
        for i,f in enumerate(self.gen_summary.get_result_files()):
            update_list.append(f.replace('\\','/'))
        
        self.window['files'].update(list(update_list),row_colors=self.filelist_bgcolor)
    
    def update_coloring_status(self,current,total):
        self.window['state'].update(self.i18n('message.coloring')+' ('+str(current)+'/'+str(total)+') ', text_color='#000000')
        
    def color_file(self,parent,i:int, f:str):
        
        gen_summary_local = GenSummary(parent.start)        
        
        try:
            img = Image.open(f)
        except Exception:
            print(f'{self.i18n("log.file.not.ocr_found")} ({f})')
            return
        if gen_summary_local.is_result(img):
            gen_summary_local.cut_result_parts(img)
            res = gen_summary_local.ocr()
            if res != False:
                with parent.color_lock :
                    parent.filelist_bgcolor[i][1] = '#dddddd'
                    parent.filelist_bgcolor[i][2] = '#333333'
                title = res
                cur,pre = gen_summary_local.get_score(img)
                ts = os.path.getmtime(f)
                now = datetime.datetime.fromtimestamp(ts)
                fmtnow = format(now, "%Y%m%d_%H%M%S")
                for ch in ('\\', '/', ':', '*', '?', '"', '<', '>', '|'):
                    title = title.replace(ch, '')
                for ch in (' ', '　'):
                    title = title.replace(ch, '_')
                dst = f"{parent.settings['autosave_dir']}/sdvx_{title[:120]}_{gen_summary_local.difficulty.upper()}_{gen_summary_local.lamp}_{str(cur)[:-4]}_{fmtnow}.png"
                try:
                    os.rename(f, dst)
                except Exception:
                    print(f'{parent.i18n("log.filename.exists")} ({dst})')
                
                with parent.color_lock : parent.ocr_found += 1
            else :
                with parent.color_lock :
                    parent.ocr_not_found += 1  
        else:
            with parent.color_lock :
                parent.filelist_bgcolor[i][1] = '#dddddd'
                parent.filelist_bgcolor[i][2] = '#333333'
            
        
#        with parent.color_lock :
#            parent.ocr_processed += 1
        return [parent.filelist_bgcolor[i][1],parent.filelist_bgcolor[i][2],parent.ocr_found,parent.ocr_not_found]
            
    
    # ファイル一覧に対し、OCR結果に応じた色を付ける
    def do_coloring(self):
        self.gen_summary.load_hashes()
        result_files = list(self.gen_summary.get_result_files())
        
        ocr_processed = 0
        
        dt_start = datetime.datetime.now()
        
        # Not as fast as I would though. Python threads...
#        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
#            future_color = {executor.submit(self.color_file, self, i, f): (i,f) for i,f in enumerate(result_files)}
#            for future in concurrent.futures.as_completed(future_color) :
#                ocr_processed += 1
#                inputs = future_color[future]
#                outputs = future.result()
#                self.filelist_bgcolor[inputs[0]][1] = outputs[0]
#                self.filelist_bgcolor[inputs[0]][2] = outputs[1]
#                self.ocr_found += outputs[2]
#                self.ocr_not_found += outputs[3] 
#                self.update_coloring_status(ocr_processed,len(result_files))

        # Non threaded mode, the original mode
        for i,f in enumerate(result_files) :
            self.color_file(self, i, f)
            self.update_coloring_status(i+1,len(result_files))
                       
        self.apply_coloring()
         
        dt_end = datetime.datetime.now()
        duration = dt_end - dt_start
         
        self.window['state'].update(self.i18n('message.coloring.complete',self.ocr_not_found,self.ocr_found,round(duration.total_seconds(),2)), text_color='#000000')
        

    def register_song(self, val, len, str, print, music, hash_jacket, hash_info):
        difficulty = val['combo_difficulty']
        print(difficulty, hash_jacket, hash_info)
        pat = re.compile(r'[0-9a-f]{16}')
        if (difficulty != '') and bool(pat.search(hash_jacket)):
            # TODO ジャケットなしの曲はinfoを登録する
            self.send_webhook(music, difficulty, hash_jacket, hash_info)
            if music not in self.musiclist['jacket'][difficulty].keys():
                self.window['state'].update(f'{self.i18n("message.song.registered")} ({music} / {hash_jacket})', text_color='#000000')
                print(self.i18n('log.song.registered'))
                for i, diff in enumerate(diff_table):
                    self.num_added_fumen += 1
                    self.musiclist['jacket'][diff][music] = str(hash_jacket)
                    if hash_info != '':
                        self.musiclist['info'][diff][music] = str(hash_info)
                
                if len(val['files']) > 0:
                    self.filelist_bgcolor[val['files'][0]][-2] = '#dddddd'
                    self.filelist_bgcolor[val['files'][0]][-1] = '#333399'
                    self.window['files'].update(row_colors=self.filelist_bgcolor)
            else:
                self.num_added_fumen += 1
                self.window['state'].update(f'{self.i18n("message.song.already.registered")} {difficulty} {self.i18n("message.hash.fixed")} ({music} / {hash_jacket})', text_color='#000000')
                print(f'{self.i18n("log.song.already.registered")} ({difficulty}) {self.i18n("log.hash.fixed")}')
                self.musiclist['jacket'][difficulty][music] = str(hash_jacket)
                if hash_info != '':
                    self.musiclist['info'][difficulty][music] = str(hash_info)
                if len(val['files']) > 0:
                    self.filelist_bgcolor[val['files'][0]][-2] = '#dddddd'
                    self.filelist_bgcolor[val['files'][0]][-1] = '#333399'
                    self.window['files'].update(row_colors=self.filelist_bgcolor)
            self.window['num_added_fumen'].update(self.num_added_fumen)
            self.save()
            self.window['hash_jacket'].update('')
            self.window['hash_info'].update('')
            self.window['txt_title'].update('')
        else:
            print('難易度 or ハッシュ値エラー')
            self.window['state'].update(self.i18n('message.error.cannot.obtain'), text_color='#000000')

    def main(self):
        while True:
            ev, val = self.window.read()
            if ev in (sg.WIN_CLOSED, 'Escape:27', '-WINDOW CLOSE ATTEMPTED-', 'btn_close_info', 'btn_close_setting'):
                self.save()
                if self.num_added_fumen > 0:
                    self.send_pkl()
                break
            elif ev == 'files': # ファイル選択時
                if len(val[ev]) > 0:
                    f = self.window['files'].get()[val[ev][0]]
                    try:
                        img = Image.open(f)
                        if self.gen_summary.is_result(img):
                            parts = self.gen_summary.cut_result_parts(Image.open(f))
                            parts['jacket_org'].resize((100,100)).save('out/tmp_jacket.png')
                            parts['info'].save('out/tmp_info.png')
                            parts['difficulty_org'].save('out/tmp_difficulty.png')
                            self.window['jacket'].update('out/tmp_jacket.png')
                            self.window['info'].update('out/tmp_info.png')
                            self.window['difficulty'].update('out/tmp_difficulty.png')
                            self.window['state'].update('')
                            self.window['hash_jacket'].update(str(imagehash.average_hash(parts['jacket_org'],10)))
                            self.window['hash_info'].update(str(imagehash.average_hash(parts['info'],10)))
                            res_ocr = self.gen_summary.ocr()
                            if self.gen_summary.difficulty != False:
                                self.window['combo_difficulty'].update(self.gen_summary.difficulty)
                            else:
                                self.window['combo_difficulty'].update('')
                            print(res_ocr)
                            if res_ocr == False:
                                self.window['state'].update(self.i18n('message.song.not.registered'), text_color='#ff0000')
                            else:
                                self.window['state'].update('')
                            #diff = parts['difficulty_org'].crop((0,0,70,30))
                            #rsum = np.array(diff)[:,:,0].sum()
                            #gsum = np.array(diff)[:,:,1].sum()
                            #bsum = np.array(diff)[:,:,2].sum()
                            #self.window['state'].update(f"sum (r,g,b)=({rsum}, {gsum}, {bsum})", text_color='#000000')
                        else:
                            self.window['jacket'].update(None)
                            self.window['info'].update(None)
                            self.window['difficulty'].update(None)
                            self.window['state'].update(self.i18n('message.non.result.images'), text_color='#ff0000')
                    except Exception:
                        self.window['state'].update(self.i18n('message.error.file.not.ocr_found'), text_color='#ff0000')
                        print(traceback.format_exc())
            elif ev == 'musics':
                if len(val['musics']) > 0:
                    self.window['txt_title'].update(self.get_musiclist()[val['musics'][0]][0])
            elif ev == 'filter':
                self.window['musics'].update(self.get_musiclist())
                self.get_dblist()
            elif ev == 'clear':
                self.window['filter'].update('')
                self.window['musics'].update(self.get_musiclist())
                self.get_dblist()
            elif ev == 'coloring':
                self.ocr_found = 0
                self.ocr_not_found = 0
                self.th_coloring = threading.Thread(target=self.do_coloring, daemon=True)
                self.th_coloring.start()
                self.window['state'].update(self.i18n('message.coloring'), text_color='#000000')
            elif ev == 'register':
                music = self.window['txt_title'].get()
                if music != '':
                    hash_jacket = self.window['hash_jacket'].get()
                    hash_info = self.window['hash_info'].get()
                    
                    tmp = Image.open('resources/images/no_jacket.png')
                    hash_no_jacket = imagehash.average_hash(tmp,10)
                    
                    if hash_jacket == str(hash_no_jacket) :
                        self.window['state'].update(self.i18n('message.song.jacketJHashIsNotFound',music))
                    else :    
                        self.register_song(val, len, str, print, music, hash_jacket, hash_info)
                else:
                    self.window['state'].update(self.i18n('message.error.no.title'), text_color='#000000')
            elif ev == 'combo_diff_db': # hash値リスト側の難易度設定を変えた時に入る
                self.get_dblist()
            elif ev == 'merge': # pklのマージボタン
                self.merge_musiclist()
            elif ev == 'send_pkl':
                self.save()
                self.send_pkl()
            elif ev == 'locale':
                self.bundle = PoorManResourceBundle(val['locale'].lower())
                self.default_locale = val['locale']
                self.i18n = self.bundle.get_text
                self.window.close()
                self.gui(False)
                self.apply_coloring()
                
                

if __name__ == '__main__':
    a = Reporter(chk_update = False)
