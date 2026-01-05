#!/usr/bin/python3
# musiclist.pklに手動追加するやつなど
import pickle
from gen_summary import *
from datetime import datetime, timedelta
import imagehash
from PIL import Image
from sha_generator import SHAGenerator

def load():
    ret = None
    with open('resources/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def save(dat:dict):
    with open('resources/musiclist.pkl', 'wb') as f:
        pickle.dump(dat, f)
        
def calculate_jacket_sha() :
    """
        a['jacket']['diff']['title'] = hash
        a['info']['diff']['title'] = hash
    """
    
    root_folder = 'D:/Tools/SoundVoltex/sdvx_helper/jackets'
    
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    generator = SHAGenerator()
    music_list =  load()
    hashes = []
    
    for i,jacket in enumerate(results):
        sha = generator.generate_sha256_from_image(root_folder+f'/{jacket}')
        
        for diff in music_list['jacket'] :
            for title in music_list['jacket'][diff] :
                if music_list['jacket'][diff][title] == str(jacket[:-4]):
                    
                    if 'jacket_sha' not in music_list :                   
                        music_list['jacket_sha'] = {}
                    
                    if diff not in music_list['jacket_sha']:
                        music_list['jacket_sha'][diff] = {}
                        
                    if title not in music_list['jacket_sha'][diff]:
                        music_list['jacket_sha'][diff][title] = sha
                                
                    print(f'SHA-256 for {jacket} is {sha} in {title}-{diff}')
        
        if sha in hashes:
            print(f">>> Duplicate hash found {sha}")
        else :
            hashes.append(sha)
            
    print(f'Generated {len(hashes)} hashes for {i} files')
    save(music_list)
        
    
        
def calculate_jacket_hash() :
    
    root_folder = 'D:/Tools/SoundVoltex/sdvx_helper/jackets'
    
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    with open(root_folder+'/0b4341982f37aefe33fef630d.png', 'rb') as f:
        # Create a SHA256 hash object
        hasher = hashlib.sha256()
        
        # Read the file in chunks and update the hash object
        for chunk in iter(lambda: f.read(4096), b''):
            hasher.update(chunk)
        
            # Get the hexadecimal representation of the hash
            hashed_file = hasher.hexdigest()
        
        # Print the hashed file
        print(hashed_file)
    
        
def update_saved_jackets() :
    
    root_folder = 'D:/Tools/SoundVoltex/sdvx_helper/jackets'
    
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    for jacket in results:
        
        if(len(jacket) == 29) :
            continue;
          
        img = Image.open(root_folder+'/'+jacket)             
        new_size_hash = imagehash.average_hash(img,10)
        
        if os.path.exists(root_folder+'/'+str(new_size_hash)+".png") :
            print(f'File already exists with name {new_size_hash}. Deleting old file with hash {jacket}...')
            os.remove(root_folder+'/'+jacket)
        else :
            print(f'No file exists with hash {new_size_hash}. Renaming {jacket}...')
            os.rename(root_folder+'/'+jacket,root_folder+'/'+str(new_size_hash)+'.png')
        


def update_music_list_hash_size(hash_size:int=10) :      
    """
        a['jacket']['diff']['title'] = hash
        a['info']['diff']['title'] = hash
    """
    a = load()

    root_folder = 'D:/Tools/SoundVoltex/results'

    start = datetime(year=2023, month=10, day=15, hour=0)
    gen_summary = GenSummary(start, root_folder + '/sync', 'true', 255, 2)
    
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    updated_hashes = 0
    
    for playScreenshotFileName in results:  
        img = Image.open(root_folder+'/'+playScreenshotFileName)
        
        if gen_summary.is_result(img):
            parts = gen_summary.cut_result_parts(img)
            res = gen_summary.ocr(hash_size=8)
            if res != False:
                title = res
                diff = gen_summary.difficulty
                
                existing_hash = a['jacket'][diff][title]
                
                if len(str(existing_hash)) == 16 :
                    
                    print(f'[{title}-{diff}] Old hash: {existing_hash}')
                    new_hash = imagehash.average_hash(parts['jacket_org'],hash_size)
                    new_info = imagehash.average_hash(parts['info'],hash_size)
                    a['jacket'][diff][title] = str(new_hash)
                    a['info'][diff][title] = str(new_hash)
                    print(f'[{title}-{diff}] New hash: {new_hash}')
                    updated_hashes += 1
        
        

    
    print(f'Updated {updated_hashes} hashes.')
    save(a)        

if __name__ == '__main__':
    #update_saved_jackets()
    calculate_jacket_sha()

    