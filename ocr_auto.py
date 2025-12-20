import easyocr
import datetime
import os, sys, re
import datetime
import logging, logging.handlers, traceback
from typing import Union
from genericpath import exists

log_dir = 'log'
log_path = os.path.join(log_dir, 'auto_ocr.log')
os.makedirs(log_dir, exist_ok=True)
if not os.path.exists(log_path):
    open(log_path, 'w').close() 

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
hdl = logging.handlers.RotatingFileHandler(
    log_path,
    encoding='utf-8',
    maxBytes=1024 * 1024 * 2,
    backupCount=1,
)
hdl.setLevel(logging.INFO)
hdl_formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(funcName)s() %(message)s')
hdl.setFormatter(hdl_formatter)
logger.addHandler(hdl)

class AutoOCR:
    
    ocr_parser_titles = None
    ocr_parser_levels = None
    init = False
    
    def __init__ (self):
        
        dt_start = datetime.datetime.now()
        self.ocr_parser_titles = easyocr.Reader(['en','ja'], gpu=False, download_enabled=True)
        logger.info('JA,EN OCR initialised')
        
        self.ocr_parser_levels = easyocr.Reader(['en'], gpu=False, download_enabled=True)
        logger.info('EN OCR initialised')
        dt_end = datetime.datetime.now()
        
        logger.info(f'OCR models loaded in {(dt_end-dt_start).total_seconds():.2f}s')
        
        init = True
        
    def parse_title(self, path:str) -> Union[str, str]:
        
        if not os.path.exists(path) or not init:
            return 'Unknown', 'Unknown' 
        
        dt_start = datetime.datetime.now()
        
        result = self.ocr_parser_titles.readtext(path,detail=0)
        
        dt_end = datetime.datetime.now()
        
        title = result[0]
        composer = result[1]
        
        logger.info(f'Identified title: {title} and composer: {composer} from {path} in {(dt_end-dt_start).total_seconds()*1000:.2f}ms')
        
        return title, composer
    
    def parse_level(self, path:str) -> Union[str, str]:
        
        if not os.path.exists(path) or not init:
            return 'Unknown', 'Unknown'
        
        dt_start = datetime.datetime.now()
        
        result = self.ocr_parser_levels.readtext(path,detail=0)
        
        dt_end = datetime.datetime.now()
        
        level = result[0]
        difficulty = result[1].upper()
        
        logger.info(f'Identified level: {level} and difficulty: {difficulty} from {path} in {(dt_end-dt_start).total_seconds()*1000:.2f}ms')
        
        return level, difficulty
        
        
        

if __name__ == '__main__':
    a = AutoOCR()
    title, composer = a.parse_title('D:/Tools/SoundVoltex/sdvx_helper/out/select_title.png')
    
    print(f'Title: {title}')
    print(f'Composer: {composer}')
    
    level, difficulty = a.parse_level('D:/Tools/SoundVoltex/sdvx_helper/out/select_level.png')
    
    print(f'Level: {level}')
    print(f'Dificulty: {difficulty}')
        