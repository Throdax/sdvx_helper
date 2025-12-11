import urllib 
import requests
import re
import special_titles
from bs4 import BeautifulSoup

def get_version(app:str) -> str:        
    
    SWVER = None
    
    with open('version.properties', 'r') as f:
        line = f.readline()
        
        while line != "" :
            if line.split("=")[0].strip() == app :
                SWVER = line.split("=")[1].strip()
            line = f.readline()
        
        if SWVER is None:
            raise ValueError
        
        return SWVER
    
def get_latest_version() -> str:
    """GitHubから最新版のバージョンを取得する。

    Returns:
        str: バージョン番号
    """
    ret = None
    url = 'https://github.com/Throdax/sdvx_helper/tags'
    r = requests.get(url)
    soup = BeautifulSoup(r.text,features="html.parser")
    for tag in soup.find_all('a'):
        if 'releases/tag/' in tag['href']:
            ret = tag['href'].split('/')[-1]
            break # 1番上が最新なので即break
    return ret

def compare_version(ver1:str, ver2:str) -> int:
    ver1_splits = re.split(r'\.',ver1)
    ver2_splits = re.split(r'\.',ver2)
    
            
    if int(ver1_splits[0]) == int(ver2_splits[0]) :
        if int(ver1_splits[1]) == int(ver2_splits[1]) :
            if int(ver1_splits[2]) == int(ver2_splits[2]) :
                return 0
            else :
                comparing_index = 2
        else :
            comparing_index = 1
    else :
        comparing_index = 0
    
    return -1 if int(ver1_splits[comparing_index]) > int(ver2_splits[comparing_index]) else 1

def find_song_rating(song_title, song_difficulty, song_list, logger):
        
        rating = 0
        
        restored_song_title = restore_title(song_title)
        
        # Find the numeric value of the song rating based on it's difficulty category
        song = song_list['titles'].get(restored_song_title, None)
        
        if song is not None:
            if song_difficulty == 'nov': 
                rating = song[3]
            elif song_difficulty == 'adv':
                rating = song[4]
            elif song_difficulty == 'exh':
                rating = song[5]
            else:
                rating = song[6]
                    
        if rating == 0:
            if logger is not None:
                logger(f'[{restored_song_title}-{song_difficulty.upper()}] Could not find song in song list for rating. Searching for direct overrides...')
            
            override = direct_overides.get(restored_song_title)
            
            if override is not None:
                for override_rating in override:
                    if override_rating.split(":")[0].lower() == song_difficulty.lower():
                       rating = override_rating.split(":")[1]
                       if logger is not None:
                           logger(f'[{restored_song_title}-{song_difficulty.upper()}] Direct override found with rating {rating}')
                       break
                   
                if rating == 0 and logger is not None:
                    logger(f'[{restored_song_title}-{song_difficulty.upper()}] No rating found for {song_difficulty.upper()}')
                   
            elif logger is not None:
                logger(f'[{restored_song_title}-{song_from_log.difficulty.upper()}] not found in direct override')
            
        return str(rating)
     
def restore_title( song_title): 
        return special_titles.get(song_title.strip(), song_title.strip())
    
        
    