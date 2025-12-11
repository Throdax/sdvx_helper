from pypresence import Presence
from pypresence.types import ActivityType, StatusDisplayType
import time
import requests
from params_secret import *

class SDVX_discord_presence:
    
    def __init__(self):
        self.client_id = discord_presence_client_id
        self.litterbox_url = "https://litterbox.catbox.moe/resources/internals/api.php"
        self.RPC = Presence(self.client_id)  # Initialize the Presence client
        self.RPC.connect()  # Start the handshake loop

        print("Connected to discord presence")
        self.start_time = int(time.time()) 
        
    def update_custom(self, custom_text:str):
        
        self.RPC.update(
            activity_type=ActivityType.PLAYING,
            status_display_type=StatusDisplayType.NAME,
            name="Sound Voltex Exceed Gear",
            details=custom_text,
            large_image='generic_cover',
            start=self.start_time)

        print(f"Presence updated with: {custom_text}")
        

    def update(self, cover_url:str='generic_cover', title:str=None, difficulty:str=None, level:str=None, score:int=None, composer:str=None):
        
        if title is None:
            update_title = 'Selecting a song'
            #status_display_type=StatusDisplayType.NAME
        else:
            update_title = f'Playing: {title}-{difficulty}-{level}'
            #StatusDisplayType.DETAILS
        
        if not score is None: 
            score = f'Score : {str(score)}'
            
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
        
        if path is None:
            raise AttributeError
        
        
        with open(path, 'rb') as cover:
            response = requests.post(self.litterbox_url, data={'reqtype': 'fileupload', 'time': "1h"}, files={'fileToUpload': cover})
            
            print(f'Cover {path} uploaded to {response.text}')
            
            return response.text
        
    def destroy(self):
        self.RPC.close()
        print("Disconnected from discord presence")


if __name__ == '__main__':
    a = SDVX_discord_presence()
    upload_url = a.upload_cover('D:/workspace/sdvx_helper/resources/images/main_sp_mini.jpg')
    a.update(cover_url=upload_url)
    
    time.sleep(30)
    a.destroy()
