import urllib 
import requests
import re
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
    
    
        
    