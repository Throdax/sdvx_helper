from pypresence import Presence
from pypresence.types import ActivityType, StatusDisplayType
import time
import requests
import params_secret
import enum

class PlayStates(enum.Enum):
    OTHER = 0
    SELECT = 1
    PLAY = 2
    RESULT = 3
    DETECT = 4
    

class SDVXDiscordPresence:
    
    init = False
    
    def __init__(self):
        
        if params_secret.discord_presence_client_id is None or params_secret.discord_presence_client_id == "":
            print('No discord client key defined. Presence cannot be used')
            return
        
        self.client_id = params_secret.discord_presence_client_id
        self.litterbox_url = "https://litterbox.catbox.moe/resources/internals/api.php"
        self.RPC = Presence(self.client_id)  # Initialize the Presence client
        self.RPC.connect()  # Start the handshake loop

        print("Connected to discord presence")
        self.start_time = int(time.time()) 
        self.init = True
        
    def update_custom(self, custom_text:str):
        
        if not self.init:
            return
        
        self.RPC.update(
            activity_type=ActivityType.PLAYING,
            status_display_type=StatusDisplayType.NAME,
            name="Sound Voltex Exceed Gear",
            details=custom_text,
            large_image='generic_cover',
            start=self.start_time)

        print(f"Presence updated with: {custom_text}")
        

    def update(self, cover_url:str='generic_cover', title:str=None, difficulty:str=None, level:str=None, score:int=None, composer:str=None, lamp:str=None, state:PlayStates=PlayStates.OTHER):
        
        
        if not self.init:
            return
        
        if  (state == PlayStates.OTHER or state == PlayStates.SELECT) and title is None:
            update_title = 'Selecting a song'
        
        elif  state == PlayStates.OTHER or state == PlayStates.SELECT:
            update_title = title
        
        elif state == PlayStates.PLAY or state == PlayStates.DETECT:
            update_title = f'Playing: {title} - {difficulty.upper()} - {level}'
        
        elif state == PlayStates.RESULT:
            update_title = f'Finished: {title} - {difficulty.upper()} - {level}'
        
        if state == PlayStates.RESULT and score is not None:
            # Need to format the score to the 1st 4 digits 
            score = f'Result : {lamp.upper()} - {str(score)[:4]}'
            
        self.RPC.update(
            activity_type=ActivityType.PLAYING,
            status_display_type=StatusDisplayType.NAME,
            name="Sound Voltex Exceed Gear",
            details=update_title,
            state=score,
            large_image=cover_url,
            large_text=composer,
            start=self.start_time)

        print(f"Presence updated with: {title}, {composer}, {score}, {cover_url}")
    
    def upload_cover(self, path:str):
        
        if not self.init:
            return
        
        if path is None:
            raise AttributeError
        
        with open(path, 'rb') as cover:
            response = requests.post(self.litterbox_url, data={'reqtype': 'fileupload', 'time': "1h"}, files={'fileToUpload': cover})
            
            print(f'Cover {path} uploaded to {response.text}')
            
            return response.text
        
    def destroy(self):
        
        if not self.init:
            return
        
        self.RPC.close()
        print("Disconnected from discord presence")


if __name__ == '__main__':
    a = SDVXDiscordPresence()
    upload_url = a.upload_cover('D:/workspace/sdvx_helper/resources/images/main_sp_mini.jpg')
    a.update(cover_url=upload_url)
    
    time.sleep(30)
    a.destroy()
