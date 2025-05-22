#!/usr/bin/python3
# musiclist.pklに手動追加するやつなど
import pickle
from gen_summary import *

def load():
    ret = None
    with open('resources/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def save(dat:dict):
    with open('resources/musiclist.pkl', 'wb') as f:
        pickle.dump(dat, f)

if __name__ == '__main__':
    """
        a['jacket']['diff']['title'] = hash
        a['info']['diff']['title'] = hash
    """
    a = load()

    root_folder = 'D:/Tools/SoundVoltex/results'

    start = datetime(year=2023, month=10, day=15, hour=0)
    gen_summary = GenSummary(start, rootFolder + '/sync', 'true', 255, 2)
    
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    updated_hashes = 0
    
    for playScreenshotFileName in results:  
        img = Image.open(root_folder+'/'+playScreenshotFileName)
        
        if gen_summary.is_result(img):
            gen_summary.cut_result_parts(img)
            res = gen_summary.ocr()
            if res != False:
                title = res
                diff = gen_summary.difficulty
                
                existing_hash = a['jacket'][diff][title]
                
                if len(existing_hash) == 64 :
                    print(f'Old hash found for {title} {diff}, Upading...')
                    new_hash = gen_summary.hash_jacket
                    new_info = gen_summary.hash_info
                    a['jacket'][diff][title] = new_hash
                    a['info'][diff][title] = new_hash
                    print(f'Updated {title} {diff} hash to {new_hash}')
                    updated_hashes += 1
        
        

    
    print(f'Updated {updated_hashes} hashes.')
    save(a)