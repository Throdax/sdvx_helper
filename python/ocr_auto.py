import easyocr
import datetime
import os, sys, re
import datetime
import logging, logging.handlers, traceback
from typing import Union
from genericpath import exists
from cmath import log

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
        """
        Initialises two the OCR. One OCR will be set to EN/JA and the second one just to EN. 
        """
        
        dt_start = datetime.datetime.now()
        self.ocr_parser_titles = easyocr.Reader(['en','ja'], gpu=False, download_enabled=True)
        logger.info('JA,EN OCR initialised')
        
        self.ocr_parser_levels = easyocr.Reader(['en'], gpu=False, download_enabled=True)
        logger.info('EN OCR initialised')
        dt_end = datetime.datetime.now()
        
        logger.info(f'OCR models loaded in {(dt_end-dt_start).total_seconds():.2f}s')
        
        self.init = True
        
    def parse_title(self, path:str) -> Union[str, str]:
        """
        Uses the EN/JA OCRR to try and retrieve the text of an image
        
        :param path:str    - The path of the image to OCR. If path does not exist or the class is not initialised, 
                             it will return "Unknown" for both values.
        
        :return str, str   - The first string will be the detected title of the song. The second string will be the composer
        """
        
        if not os.path.exists(path) or not self.init:
            return 'Unknown', 'Unknown' 
        
        dt_start = datetime.datetime.now()
        result = self.ocr_parser_titles.readtext(path,detail=0)
        dt_end = datetime.datetime.now()
        
        title = result[0]
        composer = result[1]
        
        logger.info(f'Identified title: "{title}" and composer: "{composer}" from {path} in {(dt_end-dt_start).total_seconds()*1000:.2f}ms')
        
        return title, composer
    
    def parse_level(self, path:str) -> Union[str, str]:
        """
        Uses the EN OCRR to try and retrieve the text of an image
        
        :param path:str    - The path of the image to OCR. If path does not exist or the class is not initialised, 
                             it will return "Unknown" for both values.
        
        :return str, str   - The first string will be the detected level of the song. The second string will be the difficulty
        """
        
        if not os.path.exists(path) or not self.init:
            return 'Unknown', 'Unknown'
        
        dt_start = datetime.datetime.now()
        result = self.ocr_parser_levels.readtext(path,detail=0)       
        dt_end = datetime.datetime.now()
        
        level = result[0]
        difficulty = result[1].upper()
        
        logger.info(f'Identified level: "{level}" and difficulty: "{difficulty}" from {path} in {(dt_end-dt_start).total_seconds()*1000:.2f}ms')
        
        return level, difficulty
        
    def parse_volforce(self, path:str) -> str:
        logger.info("Soon")
        
        

if __name__ == '__main__':
    a = AutoOCR()
    title, composer = a.parse_title('D:/Tools/SoundVoltex/sdvx_helper/out/select_title.png')
    
    print(f'Title: {title}')
    print(f'Composer: {composer}')
    
    level, difficulty = a.parse_level('D:/Tools/SoundVoltex/sdvx_helper/out/select_level.png')
    
    print(f'Level: {level}')
    print(f'Dificulty: {difficulty}')
        