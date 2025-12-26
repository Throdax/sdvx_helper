from pypresence import Presence
from pypresence.types import ActivityType, StatusDisplayType
import time
import requests
from requests.exceptions import Timeout
import params_secret
import enum
import logging, logging.handlers, traceback
import os, sys, re
import datetime
import threading
from poor_man_resource_bundle import *

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
    maxBytes=1024 * 1024 * 2,
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
    is_litterbox_up = False
    last_presence_update = datetime.datetime.now()
    display_type = StatusDisplayType.NAME
    litterbox_thread = None
    
    def __init__(self, locale:str):
        """
        Initialises the class.
        If the discord_presence_client_id is not setup (at compilation time), the class will not initialise (at runtime).
        It will also connect to Discord RPC and launch a thread to ping litterbox 
        
        :param locale: Str - The locale (EN, JA, etc) to use for application messages
        """
        
        if params_secret.discord_presence_client_id is None or params_secret.discord_presence_client_id == "":
            print('No discord client key defined. Presence cannot be used')
            return
        
        self.bundle = PoorManResourceBundle(locale)
        self.i18n = self.bundle.get_text
        self.client_id = params_secret.discord_presence_client_id
        self.litterbox_base_url = "https://litterbox.catbox.moe"
        self.litterbox_api_url = f"{self.litterbox_base_url}/resources/internals/api.php"
        self.RPC = Presence(self.client_id)  # Initialize the Presence client
        self.RPC.connect()  # Start the handshake loop

        logger.info("Connected to discord presence")
        self.start_time = int(time.time()) 
        self.init = True
        
        self.create_litterbox_ping_thread()
        
    def set_display_mode(self, display_mode:DisplayMode):
        """
        Configures the present to either just display "Sound Voltex Exeed Gear" as the presence title or
        the the title of the current played song
        :param display_mode:DisplayMode the DisplayMode to set the presence to                                                                     
        """
        if display_mode == DisplayMode.DEFAULT:
            self.display_type = StatusDisplayType.NAME
        else:
            self.display_type = StatusDisplayType.DETAILS
        
    def update_custom(self, custom_text:str):
        """
        Updates Discord presence with a custom message without any other parameters and no other options
        :param custom_text:str The text to display on the presence
        """
        
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

    def update(self, cover_url:str=None, title:str=None, difficulty:str=None, level:str=None, score:int=None, score_diff:int=None, composer:str=None, lamp:str=None, state:PlayStates=PlayStates.OTHER) -> bool:
        """
        Does a presence update with the supplied parameters. If the state is PlayStates.SELECT then the update will 
        only occur if 5 seconds have passed since the last update for that state.
        
        If the class has not been initialised, it will do nothing
        
        :param cover_url:str     - The url of the cover to set as the large image. If None, the pre-stored "generic_cover" will be used. Default: None
        :param title:str         - The text to as the detail section of the presence. If None and state is PlayStates.SELECT or state == PlayStates.OTHER 
                                   it will display a generic message. Default: None
        :param difficulty:str    - The difficulty of the song to append to the presence. It will be normalised before sending. Default: None
        :param level:st          - The level of the song to append to the presence. Default: None
        :param score:int         - The score of the song that just played. Only used the state is PlayStates.RESULT. Default: None
        :param score_diff:int    - The previous score of the song that just played. Only used the state is PlayStates.RESULT. Default: None
        :param composer:str      - The composer of the song. Used as the text of the large image. Default: None
        :param lamp:str          - The result lamp of the played song. If it's EXH it will be converted to MAXXIVE. Default: None
        :param state:PlayStates  - The state of the current sdvx_helper mode. It will affect which fields are send as presence. Default: PlayStates.OTHER
        
        :return bool             - True if an update was sent to discord, false otherwise
        """
        
        can_upate = True
        
        if cover_url is None:
            cover_url = 'generic_cover'
        
        if not self.init:
            return False
        
        update_title = title
        
        if (datetime.datetime.now() - self.last_presence_update).total_seconds() < 5 and state == PlayStates.SELECT:
            can_upate = False
        
        if  (state == PlayStates.OTHER or state == PlayStates.SELECT) and title is None:
            update_title = f"{self.i18n('message.discord.presence.select')}"
            
        elif state == PlayStates.PLAY or state == PlayStates.DETECT:
            score = f'{normalize_difficulty(difficulty.upper())}-{level}'
        
        if state == PlayStates.RESULT and score is not None:
            
            update_lamp = lamp.upper()
            
            if update_lamp == 'EXH':
                update_lamp = 'MAXXIVE'
                
            signal = '-'
            
            if score - score_diff > 0:
                signal = '+'
                
            formated_score_diff = sdvx_help.format_score(score - score_diff, False)
            
            # Need to format the score to the 1st 4 or 3 digits
            if len(str(score)) == 8: 
                score = f'{normalize_difficulty(difficulty.upper())}-{level} {update_lamp}: {str(score)[:4]} ({signal}{str(formated_score_diff)})'
            else:
                score = f'{normalize_difficulty(difficulty.upper())}-{level} {update_lamp}: {str(score)[:3]} ({signal}{str(formated_score_diff)})'
                
        update_composer = composer
        if composer is not None:
            update_composer = f"{self.i18n('message.discord.presence.composer',{composer})}" 
        
        # Update only every 5s
        if can_upate: 
            self.RPC.update(
                activity_type=ActivityType.PLAYING,
                status_display_type=self.display_type,
                name="Sound Voltex Exceed Gear",
                details=update_title,
                state=score,
                large_image=cover_url,
                large_text=update_composer,
                start=self.start_time)
    
            logger.info(f"Presence updated with: Title: {title}, Composer: {composer}, Diff: {difficulty}, Lamp: {lamp}, Level: {level}, Score: {score}, Cover: {cover_url}")
            
            self.last_presence_update = datetime.datetime.now()
            
            return True
        return False
    
    def normalize_difficulty(self, difficulty:str) -> str:
        """
        Converts the difficulty string to a standard format. 
            NOVICE -> NOV
            ADVANVED -> ADV
            EXHAUST -> EHX
        Any other string is returned as is
        
        :param difficulty:str    - The text to normalise
        
        :return str    - The normalised version of difficulty
        """
        if difficulty.upper() == 'NOVICE':
            return 'NOV'
        if difficulty.upper() == 'ADVANCED':
            return 'ADV'
        if difficulty.upper() == 'EXHAUST':
            return 'EHX'
        return difficulty
    
    def upload_jacket(self, path:str):
        """
        Uploads a jacket to little box.
        If the class is not initialised or litterbox is not up, nothing will happen.
        
        :param path:str    - The path of the image to upload to litterbox as jacket of the song.
        
        :return str        - The response from the server. It can be the URL of the uploaded image or an HTTP error code.
        """
        if not self.init:
            logger.error('Presence not initialised, Cover will not uploaded')
            return
        
        if not self.is_litterbox_up:
            logger.error('Litter box is not up, Cover will not uploaded')
            return
        
        if path is None:
            raise AttributeError
        
        with open(path, 'rb') as cover:
            response = requests.post(self.litterbox_api_url, data={'reqtype': 'fileupload', 'time': "1h"}, files={'fileToUpload': cover})
            
            logger.info(f'Cover {path} uploaded to {response.text}')
            
            return response.text
    
    def ping_litterbox(self):
        """
        Sends a GET request to littlerbox to make sure it's reachable. It timeout after 5 seconds and try again. 
        If the HTTP response is not 200 it will retry later
        
        If the class is not initialised, it will return and exist the thread.
        """
        timeout_seconds = 5
        
        if not self.init:
            logger.info('Stopping litterbox pinging thread...')
            return
        
        logger.info('Checking if litterbox is up...')
        
        try:
            response = requests.get(self.litterbox_base_url, timeout=timeout_seconds)
            if response.status_code == 200:
                logger.info('Litter box is up.')
                self.is_litterbox_up = True
            else:
                if not self.init:
                    logger.info('Stopping litterbox pinging thread...')
                    return
                
                logger.error(f'Litterbox responded with HTTP {response.status_code}')
                self.handle_litterbox_not_ready(timeout_seconds)
        except Timeout:
            
            if not self.init:
                logger.info('Stopping litterbox pinging thread...')
                return
            
            self.handle_litterbox_not_ready(timeout_seconds)

    def handle_litterbox_not_ready(self, timeout:int):
        """
        Sleeps for X number of seconds and try to ping again.
        
        :param timeout:int    - How long to sleep before retrying to ping litterbox again
        """
        logger.error(f'Litter box is down or didn\'t respond HTTP 200. Will try again in {timeout} seconds')
        time.sleep(timeout)
        
        self.ping_litterbox()
            
    def create_litterbox_ping_thread(self):
        """
        Creates a thread to ping litterbox
        """
        logger.info('Launching ping_litterbox thread...')
        self.litterbox_thread = threading.Thread(target=self.ping_litterbox, name="Litterbox-ping")
        self.litterbox_thread.start()
        
    def destroy(self):
        """
        Closes the RPC connection to discord and marks the class as not initialised.
        """

        if not self.init:
            return
        
        # This will cause the pinging thread to end, if one exists
        self.init = False
        
        self.RPC.close()
        logger.info("Disconnected from discord presence")


if __name__ == '__main__':
    a = SDVXDiscordPresence()
    upload_url = a.upload_jacket('D:/workspace/sdvx_helper/resources/images/main_sp_mini.jpg')
    a.update(cover_url=upload_url)
    
    time.sleep(30)
    a.destroy()
