#!/usr/bin/python3
# musiclist.pklに手動追加するやつなど
import pickle
from gen_summary import *
from datetime import datetime, timedelta
import imagehash
from PIL import Image
from sha_generator import SHAGenerator
import shutil

def load():
    ret = None
    with open('resources/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def save(dat:dict):
    with open('resources/musiclist.pkl', 'wb') as f:
        pickle.dump(dat, f)
        
def convert_bw(image):
    
    thresh = 75
    #fn = lambda x : 255 if x > thresh else 0
    image_bw = image.convert('L')#.point(fn, mode='1')
    return image_bw
    
        
def calculate_jacket_sha_single() :
    
    jacket_ocr = 'D:/workspace/sdvx_helper/out/tmp_jacket_full.png'
    jacket_saved = 'D:/Tools/SoundVoltex/sdvx_helper/jackets/old/dc0f01e0780e0380e0fcff000.png'
    
    result1 = Image.open('D:/Tools/SoundVoltex/results/sdvx_蟲の棲む処_ADV_exh_980_20260112_182822.png')
    result2 = Image.open('D:/Tools/SoundVoltex/results/sdvx_蟲の棲む処_NOV_puc_1000_20251227_131649.png')
    
    gen_summary = GenSummary(datetime.now())  
    parts1 = gen_summary.cut_result_parts(result1)
    parts2 = gen_summary.cut_result_parts(result2)
    
    generator = SHAGenerator()
    
    part1_bw = parts1["jacket_org"]
    part2_bw = parts2["jacket_org"]
    
    #print(generator.generate_sha256_from_image(jacket_ocr))
    #print(generator.generate_sha256_from_image(jacket_saved))
    print(generator.generate_sha256_from_pil_image(part1_bw))
    print(generator.generate_sha256_from_pil_image(part2_bw))
    
    part1_bw.save("D:/workspace/sdvx_helper/out/jacket_1.png")
    part2_bw.save("D:/workspace/sdvx_helper/out/jacket_2.png")
    
    time, sha_match = generator.compare_images_pixel_difference("D:/workspace/sdvx_helper/out/jacket_1.png","D:/workspace/sdvx_helper/out/jacket_2.png",100)
    
    print(f'The SHA matches? {sha_match}. Took {time} s')
    
    
        
def calculate_jacket_sha() :
    """
        a['jacket_file']['diff']['title'] = hash
        a['info']['diff']['title'] = hash
    """
    
    gen_summary = GenSummary(datetime.now())
    
    results_folder = 'D:/Tools/SoundVoltex/results'
    root_folder = 'D:/Tools/SoundVoltex/sdvx_helper/jackets/'
    new_folder = 'D:/Tools/SoundVoltex/sdvx_helper/jackets/old'
    
    results = os.listdir(results_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(results_folder, s)))
    
    generator = SHAGenerator()
    music_list =  load()
    hashes = []
    
    for i,jacket_file in enumerate(results):
        
        if not os.path.isfile(results_folder+f'/{jacket_file}') :
            continue
        
        jacket_image = Image.open(results_folder+f'/{jacket_file}')
        
        if not gen_summary.is_result(jacket_image):
            continue
        
        
        parts = gen_summary.cut_result_parts(jacket_image)
        jacket_part = parts["jacket_org"]
        difficulty_part = parts["difficulty_org"]
        
        gen_summary.ocr()
        
        sha = generator.generate_sha256_from_pil_image(jacket_part)
        old_hash = imagehash.average_hash(jacket_part,10)
        
        for title in music_list['jacket'][gen_summary.difficulty] :
            if music_list['jacket'][gen_summary.difficulty][title] == str(old_hash):
                
                if 'jacket_sha' not in music_list :                   
                    music_list['jacket_sha'] = {}
                
                if gen_summary.difficulty not in music_list['jacket_sha']:
                    music_list['jacket_sha'][gen_summary.difficulty] = {}
                    
                if title not in music_list['jacket_sha'][gen_summary.difficulty]:
                    music_list['jacket_sha'][gen_summary.difficulty][title] = sha
                            
                print(f'SHA-256 for {old_hash} is {sha} in {title}-{gen_summary.difficulty}')
                break
        
        if os.path.exists(root_folder+f'/{old_hash}.png') :            
            shutil.copy(root_folder+f'/{old_hash}.png',new_folder+f'/{old_hash}')
        
        if sha in hashes:
            print(f">>> Duplicate hash found {sha}")
        else :
            hashes.append(sha)
            
            if os.path.exists(root_folder+f'/{old_hash}.png') :
                os.rename(root_folder+f'/{old_hash}.png',root_folder+f'/{sha}.png')
            
        
                        
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
    calculate_jacket_sha_single()

    