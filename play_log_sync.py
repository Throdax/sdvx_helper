import pickle
import os
import argparse
import shutil
import sys
import json
from os import path
from PIL import Image
from datetime import datetime, timedelta 
from sdvxh_classes import OnePlayData
from sdvxh_classes import SDVXLogger
from gen_summary import GenSummary
import xml.etree.ElementTree as ET
import special_titles
import PySimpleGUI as sg
from poor_man_resource_bundle import *
import sdvx_utils
import threading
from manage_settings import *

sdvx_logger = SDVXLogger("Throdax")
time_offset_seconds = 0

SETTING_FILE = 'settings.json'
sg.theme('SystemDefault')

SWVER = sdvx_utils.get_version("sync")


class PlayLogSync():
    
    def __init__(self):
        # TODO: Save default locale to the setting.json and loaded it here
        self.default_locale = 'EN'
        self.bundle = PoorManResourceBundle(self.default_locale)
        self.i18n = self.bundle.get_text
        self.ico=self.ico_path('icon.ico')
        self.load_settings()
        self.save_settings() # 値が追加された場合のために、一度保存
        self.do_layout()
        self.main_gui()
        
    def ico_path(self, relative_path):
        try:
            base_path = sys._MEIPASS
        except Exception:
            base_path = os.path.abspath(".")
        return os.path.join(base_path, relative_path)
        
    def logToWindow(self, msg):
        if hasattr(self,'window') :
            self.window['output'].print(msg)
        else :
            print(msg)

    def is_special_title(self, song_title):
        return song_title.strip() in special_titles
    
    def load_song_list(self, song_list):
        ret = None
        with open(f'{song_list}/musiclist.pkl', 'rb') as f:
            ret = pickle.load(f)
        return ret
    
    def load_plays_list(self, allog_folder):
        ret = None
        with open(f'{allog_folder}/alllog.pkl', 'rb') as f:
            ret = pickle.load(f)
        return ret
    
    def save(self, dat:dict, allog_folder):
        with open(f'{allog_folder}/alllog.pkl', 'wb') as f:
            pickle.dump(dat, f)
            
    def get_song_from_log(self, song_log, song_title, dificulty):
        
        all_plays_of_song = []
        
        for song_from_log in song_log:
            if song_from_log.title == song_title and song_from_log.difficulty == dificulty:
                all_plays_of_song.append(song_from_log)
                
        return all_plays_of_song
            
    def is_song_in_log(self, song_log, song_to_search, file_number):
        
        if song_to_search.title in ignored_names:
            return True
            
        song_exists = False
        # song_different_date = False
        
        all_plays_of_song = self.get_song_from_log(song_log, song_to_search.title, song_to_search.difficulty);
        
    #    for song_from_log in song_log:
    #        if song_from_log.title == restore_title(song_to_search.title) and song_from_log.difficulty == song_to_search.difficulty:
    #            all_plays_of_song.append(song_from_log)
        
        song_date = datetime.strptime(song_to_search.date, "%Y%m%d_%H%M%S")
        
        for song_from_log in all_plays_of_song:
                        
            if not "_" in song_to_search.date or len(song_to_search.date.split('_')) < 2: 
                self.logToWindow(f'Mallformed song data: {song_to_search.disp()}')
                return True
                    
            offset_log_date = datetime.strptime(song_from_log.date, "%Y%m%d_%H%M%S")
            
            # Special case for when I mess around with the TZ because of SDVX
            offset_log_date += timedelta(seconds=-time_offset_seconds)
                            
            diference_in_seconds = abs((song_date - offset_log_date).total_seconds())
            diference_in_days = abs((offset_log_date - song_date)).days
           
            if diference_in_days == 0 and diference_in_seconds < 120:
                song_exists = True
                # if song_different_date == True :
                #    print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Found: Log: {song_from_log.date} | Screenshot: {song_to_search.date}\n')
                break;
            elif diference_in_days == 0 and diference_in_seconds >= 120: 
                # print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Difference time: Log: {songLogTime} | Screenshot: {songSSTime} ({diference_in_seconds}s)')
                song_different_date = True
            elif diference_in_days > 0:
                # print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Difference date: Log: {songLogDate} | Screenshot: {songSSDate} ({diference_in_days}d)')
                song_different_date = True
    
        if song_exists == False:
            self.logToWindow(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] not found in play log')
                
        return song_exists    
       
    def print_logo(self):
        self.logToWindow(' _                  __  __           _        ____                   ')
        self.logToWindow('| |    ___   __ _  |  \\/  |_   _ ___(_) ___  / ___| _   _ _ __   ___ ')
        self.logToWindow('| |   / _ \\ / _` | | |\\/| | | | / __| |/ __| \\___ \\| | | | \'_ \\ / __|')
        self.logToWindow('| |__| (_) | (_| | | |  | | |_| \\__ \\ | (__   ___) | |_| | | | | (__ ')
        self.logToWindow('|_____\\___/ \\__, | |_|  |_|\\__,_|___/_|\\___| |____/ \\__, |_| |_|\\___|')
        self.logToWindow('            |___/                                   |___/            ')
    
    def remove_songs(self, song_log):
        self.logToWindow('--------------------------------------------------')
        self.logToWindow('Searching for direct removal songs...')
        for i, song in enumerate(song_log):
            if song.title in direct_removes:
                self.logToWindow(f'Song \'{song.title}\' marked as direct removal. Removing from log...')
                song_log.pop(i)
        
        self.logToWindow('Searching finishing.')
        self.logToWindow('--------------------------------------------------')
    
    def sync(self, song_log_folder, results_folder, rebuild):
        
        #print_logo()
        
        if os.path.isdir(results_folder): 
            root_folder = results_folder
        else:
            self.logToWindow(f'Cannot run log sync: results folder \'{results_folder}\' is not a folder')
            return
            
        if os.path.isdir(song_log_folder): 
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S') 
            backup_log_file = 'alllog.pkl.' + timestamp
            
            if not os.path.exists(f"{song_log_folder}/backup"):
              os.makedirs(f"{song_log_folder}/backup")
            
            self.logToWindow(f'Backuping log file to backup/{backup_log_file}')
            shutil.copyfile(f'{song_log_folder}/alllog.pkl', f'{song_log_folder}/backup/{backup_log_file}')
            
            song_log = self.load_plays_list(song_log_folder)
            
            if rebuild: 
                self.logToWindow('Deleting and rebuilding play log!')
                song_log.clear()
            else:
                self.remove_songs(song_log)
                        
        else:
            self.logToWindow(f'Cannot run log sync: alllog folder \'{song_log_folder}\' is not a folder')
            return
    
        self.logToWindow('Initialising OCR...')
        # When running manually, call in the settings yourself to be able to run from the IDE
        dt_start = datetime.now()
        gen_summary = GenSummary(dt_start, root_folder + '/sync', 'true', 255, 2)
        
        self.logToWindow(f'Processing {len(os.listdir(root_folder))} files from folder \'{root_folder}\'')
    
        updated_songs = 0
        processed_files = 0
        results = os.listdir(root_folder)
        results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
        
        for play_screenshot_file_name in results: 
            # We ignore files which are a summary and are not png
            if play_screenshot_file_name.find('summary') > 0:
                continue
            
            if play_screenshot_file_name.find('png') < 0:
                continue
            
            if not play_screenshot_file_name.startswith("sdvx"):
                continue
    
            name_splits = play_screenshot_file_name.split("_")
            
            if(len(name_splits) == 3):
                self.logToWindow(f'Song with no ocr: {play_screenshot_file_name}')
            
            else:
                            
                song_title = ''
                for i in range(1, len(name_splits)):
                    
                    # Read all chunks as song title until we hit and difficulty identifier
                    if name_splits[i] != 'NOV' and name_splits[i] != 'ADV' and name_splits[i] != 'EXH' and name_splits[i] != 'APPEND': 
                        song_title += name_splits[i] + ' '
                        last_index_of_name = i
                        continue
                    else:
                        break;
                try:
                # Set the rest of the data based on offset of the last chunk of the title       
                    dif = name_splits[last_index_of_name + 1]
                except:
                    self.logToWindow(f'Split error on {play_screenshot_file_name}!')
                
                # If the chunk after the difficulty is 'class' we know it's a screenshot of the Skill Analyser mode and we skip that chunk
                if name_splits[last_index_of_name + 2] == 'class':
                    last_index_of_name += 1
                    
                lamp = name_splits[last_index_of_name + 2]
                
                # It can happen that the score is empty and we have a file of type
                # sdvx_プナイプナイたいそう_NOV_failed__20250111_173755
                # In the case, consider the score 0 otherwise things might break later 
                # if the play_date chunks are not assigned correctly
                if name_splits[last_index_of_name + 3] == '':
                    score = 0
                else:
                    score = name_splits[last_index_of_name + 3]
                
                try: 
                    play_date = name_splits[last_index_of_name + 4] + '_' + name_splits[last_index_of_name + 5]
                except:
                    self.logToWindow(f'Split error on {play_screenshot_file_name}!')
                        
                play_date = play_date.removesuffix('.png')
                    
            # print(f'Read from file: {song_title} / {dif} / {lamp} / {score} / {play_date}')
            
            img = Image.open(os.path.abspath(f'{root_folder}/{play_screenshot_file_name}'))
            score_from_image = gen_summary.get_score(img)                
            
    #        if song_without_ocr : 
    #            gen_summary.ocr_only_jacket(img)
    
            if song_title != '':
                
                if self.is_special_title(song_title): 
                    for i in range(0, len(song_log)): 
                        if song_log[i].title == song_title.strip():
                            song_log.pop(i)
                            self.logToWindow(f'Removed incorrect song with title {song_title} from play log.')
                            break                                                                    
                            
                song_title = self.restore_title(song_title)
                
                play_score = score_from_image[0];
                previous_score = score_from_image[1]
                
                song_from_screenshot = OnePlayData(song_title, play_score, previous_score, lamp, dif.lower(), play_date.removesuffix('.png_'))
    
                # If the song is not in the long, with a tolerance of 120 seconds, add it to the log                
                if not self.is_song_in_log(song_log, song_from_screenshot, processed_files):
                    self.logToWindow(f'[{processed_files}] [{song_from_screenshot.title}-{song_from_screenshot.difficulty.upper()}] Adding to log with date {song_from_screenshot.date}\n')
                    song_log.append(song_from_screenshot)
                    updated_songs += 1
            
            processed_files += 1
            if processed_files % 100 == 0:
                self.logToWindow(f'{processed_files} files processed out of {len(results)} ...')
        
        dt_end = datetime.now()
        duration = dt_end - dt_start
        self.logToWindow(f'Update song log with {updated_songs} songs out of {processed_files} valid files in {round(duration.total_seconds(),2)}s')
        self.save(song_log, song_log_folder)
        
       
    def dump(self, song_log_folder:str, song_list_folder:str, outfolder:str):
        
        song_log = self.load_plays_list(song_log_folder)
        song_list = self.load_song_list(song_list_folder)
        song_log.sort(key=lambda s: s.date)
        
        song_list_element = ET.Element("songList")
        xml_tree = ET.ElementTree(song_list_element)
        plays = {}
        songs = {}
        
        self.logToWindow(f'Dumping {len(song_log)} song plays to XML...')
        for song_from_log in song_log:
            
            title = self.restore_title(song_from_log.title)
            
            rating = self.find_song_rating(song_from_log, song_list, self.logToWindow)
            song_hash_play = str(hash(title + "_" + song_from_log.difficulty + "_" + rating))
            song_hash_title = str(hash(title))
                    
            existing_play_node = plays.get(song_hash_play, None)
            existing_song_node = songs.get(song_hash_title, None)
            
            # Format the date to more similar to ISO
            song_date = datetime.strptime(song_from_log.date, '%Y%m%d_%H%M%S')
            formatted_date = song_date.strftime("%Y-%m-%d %H:%M:%S")
            
            if existing_song_node is not None:
                if existing_play_node is not None: 
                    ET.SubElement(existing_play_node, "play", score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
                else: 
                    plays_node = ET.SubElement(existing_song_node, "plays", difficulty=song_from_log.difficulty, rating=rating)
                    ET.SubElement(plays_node, "play", score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
                    plays[song_hash_play] = plays_node                            
            # If we already added this song, create new "play" entry under the same song and difficulty / rating
    #        if existing_play_node is not None:       
    #            ET.SubElement(existing_play_node,"play",score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)        
            else:
                song_node = ET.SubElement(song_list_element, "song", title=title)
                plays_node = ET.SubElement(song_node, "plays", difficulty=song_from_log.difficulty, rating=rating)
                ET.SubElement(plays_node, "play", score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
                plays[song_hash_play] = plays_node
                songs[song_hash_title] = song_node
            
        self.logToWindow(f'Writing XML to {song_log_folder}played_songs.xml')
        ET.indent(xml_tree, space="\t", level=0)
        xml_tree.write(song_log_folder + "played_songs.xml", encoding="UTF-8", xml_declaration=True)
    
    def do_layout(self):
        layout = [
                [sg.Text('Language/言語', font=(None,12)),sg.Combo(self.bundle.get_available_bundles(), key='locale', font=(None,12), default_value=self.default_locale,enable_events=True)],
                [sg.HSeparator()],
                [sg.Text('Play Log:', font=(None, 16)), sg.Input(self.settings['play_log_sync']['play_log_path'], size=(44, 1), key='play_log', font=(None, 16), tooltip='The directory containing the alllog (alllog.pkl) file')],
                [sg.Text('Results Folder:', font=(None, 16)), sg.Input(self.settings['play_log_sync']['results_folder'], size=(40, 1), key='results_folder', font=(None, 16), tooltip='The directory containing the result screenshots')],
                [sg.Button('Sync', font=(None, 16), key="sync_btn", enable_events=True), sg.Check("Rebuild", font=(None, 16), enable_events=True, key='rebuild_play_log')],
                [sg.HSeparator()],
                [sg.Text('Song List:', font=(None, 16)), sg.Input(self.settings['play_log_sync']['song_list'], size=(44, 1), key='song_list', font=(None, 16), tooltip='The directory containing the song list (musiclist.pkl) file, only used with the "dump" option')],
                [sg.Text('Output Folder:', font=(None, 16)), sg.Input(self.settings['play_log_sync']['dump_output_folder'], size=(44, 1), key='xml_output_list', font=(None, 16), tooltip='The directory where the XML will be saved')],
                [sg.Button('Dump', font=(None, 16), key="dump_btn", enable_events=True, tooltip='Dumps the alllog.pkl into an xml file')],
                [sg.HSeparator()],
                [sg.Multiline(size=(150, 28), key='output', font=(None, 9))],
                [sg.Button(self.i18n('button.exit'), font=(None,16), key="btn_exit")]
            ]
        
        self.window = sg.Window(f"Play Log Sync - {SWVER}", layout, resizable=True, grab_anywhere=True, return_keyboard_events=True, finalize=True, enable_close_attempted_event=True, size=(1024, 768), icon=self.ico)

    def main_gui(self):
        while True:
            ev, val = self.window.read()
            if ev in (sg.WIN_CLOSED, 'Escape:27', '-WINDOW CLOSE ATTEMPTED-', 'btn_close_info', 'btn_close_setting'):
                self.save_settings()
                break
            elif ev == 'sync_btn':
                self.sync_thread = threading.Thread(target=self.sync, args=(val['play_log'], val['results_folder'], val['rebuild_play_log']),daemon=True)
                self.sync_thread.start()
            elif ev == 'dump_btn':
                self.dump_thread = threading.Thread(target=self.dump, args=(val['play_log'], val['song_list'], val['xml_output_list']),daemon=True)
                self.dump_thread.start()
            elif ev == 'locale':
                self.bundle = PoorManResourceBundle(val['locale'].lower())
                self.default_locale = val['locale']
                self.i18n = self.bundle.get_text
                self.window.close()
                self.do_layout()
            elif ev == 'play_log':
                self.settings['play_log_sync']['play_log_path'] = val['play_log']
            elif ev == 'results_folder':
                self.settings['play_log_sync']['results_folder'] = val['results_folder']
            elif ev == 'song_list':
                self.settings['play_log_sync']['song_list'] = val['song_list']
            elif ev == 'xml_output_list':
                self.settings['play_log_sync']['dump_output_folder'] = val['xml_output_list']    
            elif ev == 'btn_exit':
                self.window.close();
            
                
    def load_settings(self):
        """ユーザ設定(self.settings)をロードしてself.settingsにセットする。一応返り値にもする。

        Returns:
            dict: ユーザ設定
        """
        ret = {}
        try:
            with open(SETTING_FILE) as f:
                ret = json.load(f)
                self.logToWindow(self.i18n('message.settings.loaded'))
        except Exception as e:
            logger.debug(traceback.format_exc())
            self.logToWindow(self.i18n('message.settings.not.found'))

        ### 後から追加した値がない場合にもここでケア
        for k in default_val.keys():
            if not k in ret.keys():
                self.logToWindow(f"{k} {self.i18n('message.settings.key.not.found')} ({default_val[k]} {self.i18n('message.settings.key.not.found.used')})")
                ret[k] = default_val[k]
        self.settings = ret
        return ret

    def save_settings(self):
        """ユーザ設定(self.settings)を保存する。
        """
        with open(SETTING_FILE, 'w') as f:
            json.dump(self.settings, f, indent=2)
        
        
if __name__ == '__main__':
    play_log_sync = PlayLogSync() 

