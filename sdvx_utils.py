import urllib, requests
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
    
    
        
    