#!/usr/bin/python3
# musiclist.pklに手動追加するやつなど
import pickle
import imagehash
from PIL import Image

def load():
    ret = None
    with open('resources/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def save(dat:dict):
    with open('resources/musiclist.pkl', 'wb') as f:
        pickle.dump(dat, f)

if __name__ == '__main__':
    a = load()

    ##a['titles']['title'] = ['title', 'artist', 'bpm', 6,12,15,None]
#    a['titles']['弾幕信仰'] = ['弾幕信仰', '豚乙女×BEMANI Sound Team "PON"', '', 5,13,16,18]
#    a['titles']['Blue Fire'] = ['Blue Fire', 'REDALiCE feat. 野宮あゆみ', '', 4,8,12,16]
#    a['titles']['閉塞的フレーション'] = ['閉塞的フレーション', 'Pizuya\'s Cell VS BEMANI Sound Team "dj TAKA"', '', 4,12,15,18]
#    a['titles']['SUPER HEROINE!!'] = ['SUPER HEROINE!!', 'Amateras Records vs BEMANI Sound Team "TATSUYA" feat. miko', '', 3,12,15,17]
    #a['titles']['Help me, ERINNNNNN!! #幻想郷ホロイズムver.'] = ['有頂天ビバーチェ', 'Last Note.', '266', 6,11,16,None]
    #hash = a['jacket']['nov']['Help me, ERINNNNNN!! #幻想郷ホロイズムver.']
    #print(hash)
    #a['jacket']['adv']['Help me, ERINNNNNN!! #幻想郷ホロイズムver.'] = hash
    #a['jacket']['exh']['Help me, ERINNNNNN!! #幻想郷ホロイズムver.'] = hash
    ##a['jacket']['adv']['Help me, ERINNNNNN!! #幻想郷ホロイズムver.'] = ''
    hash = a['jacket']['nov']['Starlight Express'] = ''
    print(hash)
#    
    tmp = Image.open('resources/no_jacket.png')
    hash_no_jacket = imagehash.average_hash(tmp,10)
    print(hash_no_jacket)
    
#    save(a)
