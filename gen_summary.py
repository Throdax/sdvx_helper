#!/usr/bin/python3
import glob, os, io, pickle
from PIL import Image
import imagehash
import datetime, json
import logging, logging.handlers, traceback
import numpy as np
from discord_webhook import DiscordWebhook
from params_secret import *
import sdvx_utils

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

SWVER = sdvx_utils.get_version("helper")

class GenSummary:
    
    def __init__(self, now, autosave_dir=None, ignore_rankD=None, logpic_bg_alpha=None, log_maxnum=None):
        self.start = now
        self.result_parts = False
        self.difficulty = False
        self.load_settings()
        self.load_hashes()
        
        if autosave_dir : 
            self.savedir = autosave_dir
        else :
            self.savedir = self.settings['autosave_dir']
            
        if ignore_rankD : 
            self.ignore_rankD = ignore_rankD
        else :
            self.ignore_rankD = self.settings['ignore_rankD']
            
        if logpic_bg_alpha:
            self.alpha = logpic_bg_alpha
        else :
            self.alpha = self.settings['logpic_bg_alpha']
            
        if log_maxnum :
            self.max_num = log_maxnum
        else :
            self.max_num = self.params['log_maxnum']
            
        #print(now, self.savedir)
    

    def load_settings(self):
        try:
            with open('settings.json') as f:
                self.settings = json.load(f)
            with open(self.settings['params_json'], 'r') as f:
                self.params = json.load(f)
            logger.debug(f"params={self.params}")
        except Exception as e:
            logger.debug(traceback.format_exc())
            with open('resources/params.json', 'r') as f:
                self.params = json.load(f)

    # スコアの数字及び、曲名情報のハッシュを読む
    def load_hashes(self):
        self.score_hash_small = []
        self.select_score_hash_small = []
        self.select_lamp_hash = {}
        self.score_hash_large = []
        self.bestscore_hash   = []
        for i in range(10):
            self.score_hash_small.append(imagehash.average_hash(Image.open(f'resources/images/result_score_s{i}.png')))
            self.select_score_hash_small.append(imagehash.average_hash(Image.open(f'resources/images/select_score_s{i}.png')))
            self.score_hash_large.append(imagehash.average_hash(Image.open(f'resources/images/result_score_l{i}.png')))
            self.bestscore_hash.append(imagehash.average_hash(Image.open(f'resources/images/result_bestscore_{i}.png')))
        for k in ['puc', 'uc']:
            self.select_lamp_hash[k] = imagehash.average_hash(Image.open(f'resources/images/select_lamp_{k}.png'))

        try:
            with open('resources/musiclist.pkl', 'rb') as f:
                self.musiclist = pickle.load(f)
        except:
            print('musiclist読み込み時エラー。新規作成します。')
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

        # 譜面毎のハッシュ一覧を作っておく(検索用)
        # keyはハッシュ値、右辺は曲名
        self.musiclist_hash = {}
        self.musiclist_hash['jacket'] = {}
        self.musiclist_hash['jacket']['nov'] = {}
        self.musiclist_hash['jacket']['adv'] = {}
        self.musiclist_hash['jacket']['exh'] = {}
        self.musiclist_hash['jacket']['APPEND'] = {}
        self.musiclist_hash['info'] = {}
        self.musiclist_hash['info']['nov'] = {}
        self.musiclist_hash['info']['adv'] = {}
        self.musiclist_hash['info']['exh'] = {}
        self.musiclist_hash['info']['APPEND'] = {}
        for pos in ('jacket', 'info'):
            for diff in ('nov', 'adv', 'exh', 'APPEND'):
                for s in self.musiclist[pos][diff].keys():
                    self.musiclist_hash[pos][diff][self.musiclist[pos][diff][s]] = s

    def get_detect_points(self, name):
        sx = self.params[f'{name}_sx']
        sy = self.params[f'{name}_sy']
        ex = self.params[f'{name}_sx']+self.params[f'{name}_w']-1
        ey = self.params[f'{name}_sy']+self.params[f'{name}_h']-1
        return (sx,sy,ex,ey)

    # スコアの抽出
    # PIL.Imageを受け取ってintのスコアを返す
    # resources/images/result_score_{l,s}{0-9}.pngはグレースケールなので注意    
    def get_score(self, img):
        img_gray = img.convert('L')
        tmp = []
        tmp.append(img_gray.crop(self.get_detect_points('result_score_large_0')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_large_1')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_large_2')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_large_3')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_small_4')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_small_5')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_small_6')))
        tmp.append(img_gray.crop(self.get_detect_points('result_score_small_7')))
        out = []
        for j,t in enumerate(tmp):
            hash = imagehash.average_hash(t)
            minid = -1
            minval = 999999
            if j < 4:
                for i,h in enumerate(self.score_hash_large):
                    val = abs(h - hash)
                    minid = i if val<minval else minid
                    minval = val if val<minval else minval
            else:
                for i,h in enumerate(self.score_hash_small):
                    val = abs(h - hash)
                    minid = i if val<minval else minid
                    minval = val if val<minval else minval
            out.append(minid)
        cur_score = int(''.join(map(str, out)))

        # bestスコアの処理
        tmp = []
        out = []
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_0')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_1')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_2')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_3')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_4')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_5')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_6')))
        tmp.append(img_gray.crop(self.get_detect_points('result_bestscore_7')))
        #for j,t in enumerate(tmp):
        #    hash = imagehash.average_hash(t)
        #    t.save(f"result_bestscore_{hash}.png")
        for j,t in enumerate(tmp):
            hash = imagehash.average_hash(t)
            minid = -1
            minval = 999999
            for i,h in enumerate(self.bestscore_hash):
                val = abs(h - hash)
                minid = i if val<minval else minid
                minval = val if val<minval else minval
            if minid in (9,8): # 8,9の判定を間違えやすいので、左下の色を見て判別
                if np.array(t)[10][1] < 100:
                    minid = 9
                else:
                    minid = 8
            out.append(minid)
        pre_score = int(''.join(map(str, out)))

        return cur_score, pre_score
    
    def get_score_on_select(self, img):
        """選曲画面における自己べスコア、ランプの取得

        Args:
            img (PIL.Image): キャプチャ画像

        Returns:
            int: スコア
        """
        score = 0
        lamp = False
        img_gray = img.convert('L')
        tmp = []
        tmp.append(img_gray.crop(self.get_detect_points('select_score_large_0')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_large_1')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_large_2')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_large_3')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_small_4')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_small_5')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_small_6')))
        tmp.append(img_gray.crop(self.get_detect_points('select_score_small_7')))
        out = []
        for j,t in enumerate(tmp):
            hash = imagehash.average_hash(t)
            minid = -1
            minval = 999999
            if j < 4:
                for i,h in enumerate(self.score_hash_large):
                    val = abs(h - hash)
                    minid = i if val<minval else minid
                    minval = val if val<minval else minval
            else:
                for i,h in enumerate(self.select_score_hash_small):
                    val = abs(h - hash)
                    minid = i if val<minval else minid
                    minval = val if val<minval else minval
            out.append(minid)
        score = int(''.join(map(str, out)))
        # ランプ処理
        img_lamp = img.crop(self.get_detect_points('select_lamp'))
        hash = imagehash.average_hash(img_lamp)
        for k in self.select_lamp_hash.keys():
            if abs(hash - self.select_lamp_hash[k]) < 4:
                lamp = k
        if not lamp: # puc, uc以外はimagehashを使わずに判定
            a = np.array(img_lamp)[:,:,:3]
            if a.sum() > 620000:
                lamp = 'hard'
            elif a.sum() < 400000:
                lamp = 'failed'
            else:
                lamp = 'clear'

        # アーケード版かどうかの判定
        is_arcade = True
        img_arcade = img.crop(self.get_detect_points('select_arcade'))
        is_arcade = np.array(img_arcade).sum() > 100000

        return score, lamp, is_arcade

    def comp_images(self, img1, img2, threshold=10):
        val1 = imagehash.average_hash(img1)
        val2 = imagehash.average_hash(img2)
        return abs(val2-val1) < threshold
    
    def send_webhook(self,hash_size:int=10):
        try:
            if (self.result_parts != False) and self.settings['send_webhook']:
                url = url_webhook_unknown
                if self.difficulty == 'exh':
                    url = url_webhook_unknown_exh
                elif self.difficulty == 'adv':
                    url = url_webhook_unknown_adv
                elif self.difficulty == 'nov':
                    url = url_webhook_unknown_nov
                webhook = DiscordWebhook(url=url, username="unknown title info")
                msg = ''
                for i in ('jacket_org', 'info'):
                    msg += f"- **{imagehash.average_hash(self.result_parts[i],hash_size)}**\n"
                # 添付ファイル
                img_bytes = io.BytesIO()
                self.result_parts['info'].crop((0,0,260,65)).save(img_bytes, format='PNG')
                webhook.add_file(file=img_bytes.getvalue(), filename=f'info.png')
                img_bytes = io.BytesIO()
                self.result_parts['difficulty'].save(img_bytes, format='PNG')
                webhook.add_file(file=img_bytes.getvalue(), filename=f'difficulty.png')
                msg += f"(difficulty: **{self.difficulty.upper()}**, sdvx_helper:{SWVER})"

                webhook.content=msg

            res = webhook.execute()
        except Exception:
            logger.debug(traceback.format_exc())
    
    def is_result(self,img):
        cr = img.crop(self.get_detect_points('onresult_val0'))
        img_j = Image.open('resources/images/onresult.png')
        val0 = self.comp_images(cr, img_j, 5)

        cr = img.crop(self.get_detect_points('onresult_val1'))
        img_j = Image.open('resources/images/onresult2.png')
        val1 = self.comp_images(cr, img_j, 5)

        ret = val0 & val1
        if self.params['onresult_enable_head']:
            cr = img.crop(self.get_detect_points('onresult_head'))
            img_j = Image.open('resources/images/result_head.png')
            val2 = self.comp_images(cr, img_j, 5)
            ret &= val2
        return ret

    def cut_result_parts(self, img):
        parts = {}
        parts['rank'] = img.crop(self.get_detect_points('log_crop_rank'))

        # 各パーツの切り取り
        for i in ('title', 'title_small', 'difficulty', 'rate', 'score', 'jacket', 'info'):
            parts[i] = img.crop(self.get_detect_points('log_crop_'+i))

        # クリアランプの抽出
        lamp = ''
        if self.comp_images(img.crop(self.get_detect_points('lamp')), Image.open('resources/images/lamp_puc.png')):
            lamp = 'puc'
        elif self.comp_images(img.crop(self.get_detect_points('lamp')), Image.open('resources/images/lamp_uc.png')):
            lamp = 'uc'
        elif self.comp_images(img.crop(self.get_detect_points('lamp')), Image.open('resources/images/lamp_clear.png')):
            rsum = np.array(img.crop(self.get_detect_points('gauge')))[:,:,0].sum()
            gsum = np.array(img.crop(self.get_detect_points('gauge')))[:,:,1].sum()
            bsum = np.array(img.crop(self.get_detect_points('gauge')))[:,:,2].sum()
            #print(rsum, gsum, bsum)
            if rsum < gsum:
                lamp = 'clear'
            else:
                if gsum > 200000:
                    lamp = 'class_clear'
                else:
                    lamp = 'hard'
        elif self.comp_images(img.crop(self.get_detect_points('lamp')), Image.open('resources/images/lamp_failed.png')):
            lamp = 'failed'

        if lamp == '':
            return False

        # 各パーツのリサイズ
        # 上4桁だけにする
        parts['difficulty_org'] = parts['difficulty']
        parts['difficulty'] = parts['difficulty'].resize((69,15))
        parts['score']      = parts['score'].resize((86,20))
        parts['rank']       = parts['rank'].resize((37,25))
        parts['rate']       = parts['rate'].resize((80,20))
        parts['jacket_org'] = parts['jacket']
        parts['jacket']     = parts['jacket'].resize((36,36))

        parts['lamp'] = Image.open(f'resources/images/log_lamp_{lamp}.png')
        parts['lamp_small'] = parts['lamp']
        parts['score_small'] = parts['score']
        parts['rank_small'] = parts['rank']
        parts['jacket_small'] = parts['jacket']
        parts['difficulty_small'] = parts['difficulty']
        self.result_parts = parts
        self.lamp = lamp
        return parts

    def put_result(self, img, bg, bg_small, idx):
        img_d = Image.open('resources/images/rank_d.png')
        # ランクDの場合は飛ばす
        if abs(imagehash.average_hash(img.crop(self.get_detect_points('log_crop_rank'))) - imagehash.average_hash(img_d)) < 10:
            if self.ignore_rankD:
                logger.debug(f'skip! (idx={idx})')
                return False
            
        parts = self.cut_result_parts(img)
        if parts != False:
            rowsize = self.params['log_rowsize']

            for i in self.params['log_parts']:
                bg.paste(parts[i],     (self.params[f"log_pos_{i}_sx"], self.params[f"log_pos_{i}_sy"]+rowsize*idx))

            for i in self.params['log_small_parts']:
                bg_small.paste(parts[i],     (self.params[f"log_pos_{i}_sx"], self.params[f"log_pos_{i}_sy"]+rowsize*idx))
            return True
        else:
            return False

    def generate_today_all(self, dst:str):
        logger.debug(f'called! ignore_rankD={self.ignore_rankD}, savedir={self.savedir}')
        if type(dst) == str:
            try:
                # 枚数を検出
                num = 0
                bg = Image.new('RGB', (500,500), (0,0,0))
                for f in self.get_result_files():
                    img = Image.open(f)
                    ts = os.path.getmtime(f)
                    now = datetime.datetime.fromtimestamp(ts)
                    if self.start.timestamp() > now.timestamp():
                        break
                    if self.is_result(img):
                        if self.put_result(img, bg, bg, 0) != False:
                            num += 1
                print(f"検出した枚数num:{num}")
                logger.debug(f"検出した枚数num:{num}")
                if num == 0:
                    print('本日のリザルトが1枚もありません。スキップします。')
                    return False
                # 画像生成
                idx = 0
                h = self.params['log_margin']*2 + max(num,self.params['log_maxnum'])*self.params['log_rowsize']
                bg = Image.new('RGB', (self.params['log_width'],h), (0,0,0))
                bg.putalpha(self.alpha)
                bg_small = Image.new('RGB', (self.params['log_small_width'],h), (0,0,0))
                for f in self.get_result_files():
                    img = Image.open(f)
                    ts = os.path.getmtime(f)
                    now = datetime.datetime.fromtimestamp(ts)
                    if self.start.timestamp() > now.timestamp():
                        break
                    if self.is_result(img):
                        if self.put_result(img, bg, bg_small, idx) != False:
                            idx += 1
                bg.save(dst)
            except Exception as e:
                logger.error(traceback.format_exc())
            return True
    
    # ジャケット画像を与えた時のOCR結果を返す(選曲画面からの利用を想定)
    # 返り値: 曲名, hash差分の最小値
    def ocr_only_jacket(self, jacket, nov, adv, exh, APPEND,hash_size:int=10):
        hash_jacket = imagehash.average_hash(jacket,hash_size)
        title = False
        minval = 99999
        sum_nov = np.array(nov).sum()
        sum_adv = np.array(adv).sum()
        sum_exh = np.array(exh).sum()
        sum_APPEND = np.array(APPEND).sum()
        max_sum = max(sum_nov, sum_adv, sum_exh, sum_APPEND)
        if max_sum == sum_nov:
            difficulty = 'nov'
        elif max_sum == sum_adv:
            difficulty = 'adv'
        elif max_sum == sum_exh:
            difficulty = 'exh'
        else:
            difficulty = 'APPEND'
        
        # 曲名を検出
        for h in self.musiclist_hash['jacket'][difficulty].keys():
            if h != '' :
                hash_cur = imagehash.hex_to_hash(h)
                if len(hash_cur) == len(hash_jacket) and abs(hash_cur - hash_jacket) < minval:
                    minval = abs(hash_cur - hash_jacket)
                    title = self.musiclist_hash['jacket'][difficulty][h]
        return title, minval, difficulty

    def ocr_from_detect(self,hash_size:int=10):
        """曲決定画面から曲名情報を抽出。曲中で表示するライバル欄などに使う。

        Returns:
            str: 曲名
            int: 差分の最小値
            str: 難易度
        """
        jacket      = Image.open('out/select_jacket.png')
        hash_jacket = imagehash.average_hash(jacket,hash_size)
        diff        = Image.open('out/select_difficulty.png')
        target = {}
        target['nov'] = imagehash.hex_to_hash('267e7c787a787c7e')
        target['adv'] = imagehash.hex_to_hash('43478889a9b99cdf')
        target['exh'] = imagehash.hex_to_hash('436328fafa39efc6')
        target['inf'] = imagehash.hex_to_hash('367e7c7e7e7c6c6e')
        target['grv'] = imagehash.hex_to_hash('66763e3e3c7c7c7c')
        target['hvn'] = imagehash.hex_to_hash('484c04fcfcbcb6ff')
        target['mxm'] = imagehash.hex_to_hash('001099cdcdddfdef')
        target['vvd'] = imagehash.hex_to_hash('1c3c3c3c3c3c3cbc')
        hash_diff = imagehash.average_hash(diff)
        # hash差分が最小の難易度を見つける
        minval = 999
        for t in target.keys():
            val = abs(target[t] - hash_diff)
            if val < minval:
                minval = val
                difficulty = t
        if difficulty not in ('nov', 'adv', 'exh'):
            difficulty = 'APPEND'
        title       = False
        minval      = 99999

        # 曲名を検出
        for h in self.musiclist_hash['jacket'][difficulty].keys():
            hash_cur = imagehash.hex_to_hash(h)
            if len(hash_cur) == len(hash_jacket) and abs(hash_cur - hash_jacket) < minval:
                minval = abs(hash_cur - hash_jacket)
                title = self.musiclist_hash['jacket'][difficulty][h]
        logger.debug(f"title:{title}, difficulty:{difficulty}, minval:{minval}")
        return title, minval, difficulty

    def ocr(self, notify:bool=False,hash_size:int=10):
        ret = False
        difficulty = False
        detected = False
        try:
            diff = self.result_parts['difficulty_org'].crop((0,0,70,30))
            hash_nov = imagehash.average_hash(Image.open('resources/images/difficulty_nov.png'))
            hash_adv = imagehash.average_hash(Image.open('resources/images/difficulty_adv.png'))
            hash_exh = imagehash.average_hash(Image.open('resources/images/difficulty_exh.png'))
            hash_cur = imagehash.average_hash(diff)

            hash_jacket = imagehash.average_hash(self.result_parts['jacket_org'],hash_size)
            hash_info   = imagehash.average_hash(self.result_parts['info'],hash_size)
            rsum = np.array(diff)[:,:,0].sum()
            gsum = np.array(diff)[:,:,1].sum()
            bsum = np.array(diff)[:,:,2].sum()
            if (rsum<190000) and (gsum<180000) and (bsum>300000):
                difficulty = 'nov'
            elif (rsum>300000) and (gsum>260000) and (bsum<180000):
                difficulty = 'adv'
            elif (rsum>300000) and (gsum<180000) and (bsum<180000):
                difficulty = 'exh'
            else:
                difficulty = 'APPEND'
            self.difficulty = difficulty
            
            for h in self.musiclist_hash['jacket'][difficulty].keys():
                h = imagehash.hex_to_hash(h)
                
                threshold = 3
                
                # Special Help me, ERINNNNNN!! #幻想郷ホロイズムver. hash that has a lot of conflicts with シアワセうさぎ・ぺこみこマリン
                # It's basicaly the same jacket with a diferent text and the algoritm cannot handle that
                if str(h) == 'e3c87e1f9ff7c0f8367c03040' :
                    threshold = 0
                
                if len(h) == len(hash_jacket) and abs(h - hash_jacket) < threshold:
                    self.hash_hit = h
                    if self.settings['save_jacketimg']:
                        tt = f"jackets/{str(h)}.png"
                        if not os.path.exists(tt):
                            self.result_parts['jacket_org'].save(tt)
                    detected = True
                    ret = self.musiclist_hash['jacket'][difficulty][str(h)]
                    logger.debug(f"OCR pass: {abs(h - hash_jacket) < 2}, h:{str(h)}, cur:{str(hash_jacket)}, diff:{abs(h - hash_jacket) < 2}")
                    break
                elif len(h) != len(hash_jacket) :
                    logger.debug(f"Comparing old length hash (8) with new length hash ({hash_size}): {str(hash_jacket)}. Skipping...")
            if not detected:
                if notify and self.settings['send_webhook']:
                    self.send_webhook()
                # 曲名エリアからの認識だと精度が悪いので放置
                #for h in self.musiclist_hash['info'][difficulty].keys():
                #    h = imagehash.hex_to_hash(h)
                #    if abs(h - hash_info) < 5:
                #        ret = self.musiclist_hash['info'][difficulty][str(h)]
                #        #break
            else:
                tmp = Image.open('resources/images/no_jacket.png')
                hash_no_jacket = imagehash.average_hash(tmp,hash_size)
                if abs(hash_jacket - hash_no_jacket) < 5:
                    print('ジャケット削除済みの曲なので判定結果をクリアします。')
        except Exception:
            logger.debug(traceback.format_exc())
            raise Exception
        return ret
    
    # OCRの動作確認用。未検出のものを見つけて報告するために使う。
    def chk_ocr(self, iternum=500):
        logger.debug(f'called! ignore_rankD={self.ignore_rankD}, savedir={self.savedir}')
        try:
            idx = 0
            for f in self.get_result_files():
                img = Image.open(f)
                if self.is_result(img):
                    cur,pre = self.get_score(img)
                    if self.cut_result_parts(img) != False:
                        idx+=1
                        ocr_result = self.ocr()
                        print(f"{f[-19:]}: {cur:,} ({pre:,}), {ocr_result}")
                        if ocr_result == False:
                            pass
                            #self.send_webhook()
                if idx >= iternum:
                    break
        except Exception as e:
            logger.error(traceback.format_exc())

    def get_result_files(self):
        return sorted(glob.glob(self.savedir+'/sdvx_*.png'), key=os.path.getmtime, reverse=True)

    def generate(self): # max_num_offset: 1日の最後など、全リザルトを対象としたい場合に大きい値を設定する
        logger.debug(f'called! ignore_rankD={self.ignore_rankD}, savedir={self.savedir}')

        try:
            #bg = Image.open('resources/images/summary_full_bg.png')
            #bg_small = Image.open('resources/images/summary_small_bg.png')
            # 背景の単色画像を生成する場合はこれ
            h = self.params['log_margin']*2 + self.params['log_maxnum']*self.params['log_rowsize']
            bg = Image.new('RGB', (self.params['log_width'],h), (0,0,0))
            bg_small = Image.new('RGB', (self.params['log_small_width'],h), (0,0,0))
            bg.putalpha(self.alpha) #背景を透過
            bg_small.putalpha(self.alpha)
            idx = 0
            for f in self.get_result_files():
                #logger.debug(f'f={f}')
                img = Image.open(f)
                ts = os.path.getmtime(f)
                now = datetime.datetime.fromtimestamp(ts)
                # 開始時刻より古いファイルに当たったら終了
                if self.start.timestamp() > now.timestamp():
                    break
                if self.is_result(img):
                    cur,pre = self.get_score(img)
                    if self.put_result(img, bg, bg_small, idx) != False:
                        idx += 1
                        #self.send_webhook()
                    if idx >= self.max_num:
                        break
            bg.save('out/summary_full.png')
            bg_small.save('out/summary_small.png')
        except Exception as e:
            logger.error(traceback.format_exc())

    def update_musicinfo(self, img):
        """曲決定時に出る曲情報を切り出してファイルに保存する。
        """
        jacket = img.crop(self.get_detect_points('info_jacket'))
        jacket.save('out/select_jacket.png')
        title = img.crop(self.get_detect_points('info_title'))
        title.save('out/select_title.png')
        lv = img.crop(self.get_detect_points('info_lv'))
        lv.save('out/select_level.png')
        lv = img.crop(self.get_detect_points('info_diff'))
        lv.save('out/select_difficulty.png')
        bpm = img.crop(self.get_detect_points('info_bpm'))
        bpm.save('out/select_bpm.png')
        ef = img.crop(self.get_detect_points('info_ef'))
        ef.save('out/select_effector.png')
        illust = img.crop(self.get_detect_points('info_illust'))
        illust.save('out/select_illustrator.png')
        img.save('out/select_whole.png')

if __name__ == '__main__':
    start = datetime.datetime(year=2023,month=10,day=15,hour=0)
    a = GenSummary(start)
    #a.generate()
    #import glob
    #for f in glob.glob('tmp/sel_*png'):
    #    img = Image.open(f)
    #    print(f, a.get_score_on_select(img))
    #a.generate_today_all('hoge.png')
    #a.chk_ocr(60)
    for f in ['debug/profession_exh.png', 'debug/gambol_inf.png', 'debug/gorira_adv.png', 'debug/unlimi_nov.png']:
    #for f in ['debug/profession_exh.png']:
        a.update_musicinfo(Image.open(f))
        print(f, a.ocr_from_detect())