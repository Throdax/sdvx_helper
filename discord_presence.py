from pypresence import Presence
from pypresence.types import ActivityType, StatusDisplayType
import time
import requests
import params_secret
import enum
import logging, logging.handlers, traceback
import os, sys, re
import datetime

log_dir = 'log'
log_path = os.path.join(log_dir, 'presense.log')
os.makedirs(log_dir, exist_ok=True)
if not os.path.exists(log_path):
    open(log_path, 'w').close() 

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
hdl = logging.handlers.RotatingFileHandler(
    log_path,
    encoding='utf-8',
    maxBytes=1024*1024*2,
    backupCount=1,
)
hdl.setLevel(logging.INFO)
hdl_formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(funcName)s() %(message)s')
hdl.setFormatter(hdl_formatter)
logger.addHandler(hdl)


class PlayStates(enum.Enum):
    OTHER = 0
    SELECT = 1
    PLAY = 2
    RESULT = 3
    DETECT = 4
    
class DisplayMode(enum.Enum):
    DEFAULT = 0
    SONG = 1    
    

class SDVXDiscordPresence:
    
    init = False
    last_presence_update = datetime.datetime.now()
    display_type = StatusDisplayType.NAME
    
    def __init__(self):
        
        if params_secret.discord_presence_client_id is None or params_secret.discord_presence_client_id == "":
            print('No discord client key defined. Presence cannot be used')
            return
        
        self.client_id = params_secret.discord_presence_client_id
        self.litterbox_url = "https://litterbox.catbox.moe/resources/internals/api.php"
        self.RPC = Presence(self.client_id)  # Initialize the Presence client
        self.RPC.connect()  # Start the handshake loop

        logger.info("Connected to discord presence")
        self.start_time = int(time.time()) 
        self.init = True
        
    def set_display_mode(self, display_mode:DisplayMode):
        if display_mode == DisplayMode.DEFAULT:
            self.display_type = StatusDisplayType.NAME
        else :
            self.display_type = StatusDisplayType.DETAILS
            
        
    def update_custom(self, custom_text:str):
        
        if not self.init:
            return
        
        self.RPC.update(
            activity_type=ActivityType.PLAYING,
            status_display_type=self.display_type,
            name="Sound Voltex Exceed Gear",
            details=custom_text,
            large_image='generic_cover',
            start=self.start_time)

        logger.info(f"Presence updated with: {custom_text}")
        

    def update(self, cover_url:str=None, title:str=None, difficulty:str=None, level:str=None, score:int=None, score_diff:int=None, composer:str=None, lamp:str=None, state:PlayStates=PlayStates.OTHER):
        
        can_upate = True
        
        if cover_url is None:
            cover_url = 'generic_cover'
        
        if not self.init:
            return
        
        update_title = title
        
        if (datetime.datetime.now() - self.last_presence_update).total_seconds() < 5 and state == PlayStates.SELECT:
                can_upate = False
        
        if  (state == PlayStates.OTHER or state == PlayStates.SELECT) and title is None:
            update_title = '<<< Selecting next song >>>'
            
        elif state == PlayStates.PLAY or state == PlayStates.DETECT:
            score = f'{difficulty.upper()}-{level}'
        
        elif state == PlayStates.RESULT:
            update_title = f'{title}-{difficulty.upper()}-{level}'
        
        if state == PlayStates.RESULT and score is not None:
            
            update_lamp = lamp.upper()
            
            if update_lamp == 'EXH' :
                update_lamp = 'MAXXIVE'
            
            # Need to format the score to the 1st 4 or 3 digits
            if len(str(score)) == 8: 
                score = f'Result: {update_lamp} - {str(score)[:4]} ({str(score-score_diff)})'
            else :
                score = f'Result: {update_lamp} - {str(score)[:3]}  ({str(score-score_diff)})'
                
        update_composer = composer
        if composer is not None:
            update_composer = f'Composed by: {composer}' 
        
        # Update only every 5s
        if can_upate :    
            self.RPC.update(
                activity_type=ActivityType.PLAYING,
                status_display_type=self.display_type,
                name="Sound Voltex Exceed Gear",
                details=update_title,
                state=score,
                large_image=cover_url,
                large_text=update_composer,
                start=self.start_time)
    
            logger.info(f"Presence updated with: {title}, {composer}, {difficulty}, {lamp}, {level}, {score}, {cover_url}")
            
            self.last_presence_update = datetime.datetime.now()
            
            return True
        return False
    
    def upload_cover(self, path:str):
        
        if not self.init:
            return
        
        if path is None:
            raise AttributeError
        
        with open(path, 'rb') as cover:
            response = requests.post(self.litterbox_url, data={'reqtype': 'fileupload', 'time': "1h"}, files={'fileToUpload': cover})
            
            logger.info(f'Cover {path} uploaded to {response.text}')
            
            return response.text
        
    def destroy(self):
        
        if not self.init:
            return
        
        self.RPC.close()
        logger.info("Disconnected from discord presence")


if __name__ == '__main__':
    a = SDVXDiscordPresence()
    upload_url = a.upload_cover('D:/workspace/sdvx_helper/resources/images/main_sp_mini.jpg')
    a.update(cover_url=upload_url)
    
    time.sleep(30)
    a.destroy()
