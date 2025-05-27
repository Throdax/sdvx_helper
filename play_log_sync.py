import pickle
import os
import argparse
import shutil
import sys
from os import path
from PIL import Image
from datetime import datetime, timedelta 
from sdvxh_classes import OnePlayData
from sdvxh_classes import SDVXLogger
from gen_summary import GenSummary
import xml.etree.ElementTree as ET

special_titles = {
        'Death by Glamour  華麗なる死闘':  'Death by Glamour / 華麗なる死闘',
        'Electric Sister Bitch':'Electric "Sister" Bitch',
        'Lunatic Dial':'Lunartic Dial',
        'ASGORE  アズゴア':'ASGORE / アズゴア',
        'archivezip':'archive::zip',
        'Sakura Reflection (PLight Slayer Remix)':'Sakura Reflection (P*Light Slayer Remix)',
        'Spider Dance  スパイダーダンス':'Spider Dance / スパイダーダンス',
        'U.N. Owen was her (Hyuji Remix)':'U.N. Owen was her? (Hyuji Remix)',
        'I’m Your Treasure Box ＊あなたは マリンせんちょうを たからばこからみつけた。':'I’m Your Treasure Box ＊あなたは マリンせんちょうを たからばこからみつけた。',
        'The Sampling Paradise (PLight Remix)':'The Sampling Paradise (P*Light Remix)',
        'Finale  フィナーレ':'Finale / フィナーレ',
        'コンベア速度Max!しゃいにん☆廻転ズシSushi&Peace':'コンベア速度Max!? しゃいにん☆廻転ズシ"Sushi&Peace"',
        'コンベア速度Max! しゃいにん☆廻転ズシSushi&Peace':'コンベア速度Max!? しゃいにん☆廻転ズシ"Sushi&Peace"',
        'VoynichManuscript':'Voynich:Manuscript',        
        'Believe (y)our Wings {VIVID RAYS}':'Believe (y)our Wings {V:IVID RAYS}',
        'チルノのパーフェクトさんすう教室　⑨周年バージョン':'チルノのパーフェクトさんすう教室　⑨周年バージョン',
        'Wuv U(picoustic rmx)':'Wuv U(pico/ustic rmx)',
        'Battle Against a True Hero  本物のヒーローとの戦い':'Battle Against a True Hero / 本物のヒーローとの戦い',
        'rEVoltagers':'rE:Voltagers',
        'S1CK F41RY':'S1CK_F41RY',
        'ニ分間の世界':'二分間の世界',
        'ReRose Gun Shoooot!':'Re:Rose Gun Shoooot!',
        'gigadelic (かめりあ\'s The TERA RMX)':'gigadelic (かめりあ\'s "The TERA" RMX)',
        'PROVOESPROPOSE êl fine':'PROVOES*PROPOSE <<êl fine>>',
        'LuckyClover':'Lucky*Clover',
        '壊Raveit!! 壊Raveit!!':'壊Rave*it!! 壊Rave*it!!',
        'BLACK or WHITE':'BLACK or WHITE?',
        'MrVIRTUALIZER':'Mr.VIRTUALIZER',
        '#Fairy dancing in lake':'#Fairy_dancing_in_lake',
        '゜。Chantilly Fille。°':'゜*。Chantilly Fille。*°',
        '侵蝕コード 666 -今日ちょっと指 (略-':'侵蝕コード：666　-今日ちょっと指（略-',
        '侵蝕コード：666　-今日ちょっと指（略-':'侵蝕コード：666　-今日ちょっと指（略-',
        '隅田川夏恋歌IO Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        '隅田川純恋歌IO Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        '隅田川純恋歌I/O Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        '隅田川夏恋歌 (IO Angel mix)':'隅田川夏恋歌 (I/O Angel mix)',
        'ハナビラリンクス':'ハナビラ:リンクス',
        'Heartache  心の痛み':'Heartache / 心の痛み',
        'Chantilly Fille':'゜*。Chantilly Fille。*°',
        'infinite youniverse':'infinite:youniverse',
        'infiniteyouniverse':'infinite:youniverse',
        '#FairyJoke #SDVX Edit':'#FairyJoke #SDVX_Edit',
        'Spear of Justice  正義の槍':'Spear of Justice / 正義の槍'
    }

direct_overides = {
    'Pure Evil': {'NOV:5','ADV:12','EXH:17'},
    'チルノのパーフェクトさんすう教室　⑨周年バージョン':{'NOV:5','ADV:11','EXH:13','APPEND:16'},
    'チルノのパーフェクトさんすう教室 ⑨周年バージョン':{'NOV:5','ADV:11','EXH:13','APPEND:16'},
    'グレイスちゃんの超～絶!!グラビティ講座w':{'APPEND:1'},
    'マキシマ先生の満開!!ヘヴンリー講座♥':{'APPEND:1'},
    'エクシード仮面ちゃんのちょっと一線をえくしーどしたEXCEED講座':{'APPEND:1'}
    }

ignored_names = {
    'Help me, CODYYYYYY!!'
}

direct_removes = {
    '侵蝕コード：666　-今日ちょっと指（略-',    
    }

sdvx_logger = SDVXLogger("Throdax")
time_offset_seconds = 0

def restore_title(song_title):       
    return special_titles.get(song_title.strip(),song_title.strip())

def is_special_title(song_title):
    return song_title.strip() in special_titles

def load_song_list(song_list):
    ret = None
    with open(f'{song_list}/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def load_plays_list(allog_folder):
    ret = None
    with open(f'{allog_folder}/alllog.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret


def save(dat:dict, allog_folder):
    with open(f'{allog_folder}/alllog.pkl', 'wb') as f:
        pickle.dump(dat, f)
        
def get_song_from_log(song_log, song_title, dificulty):
    
    all_plays_of_song = []
    
    for song_from_log in song_log:
        if song_from_log.title == song_title and song_from_log.difficulty == dificulty:
            all_plays_of_song.append(song_from_log)
            
    return all_plays_of_song
    

        
def is_song_in_log(song_log, song_to_search,file_number):
    
    if song_to_search.title in ignored_names:
        return True
        
    song_exists = False
    #song_different_date = False
    
    all_plays_of_song = get_song_from_log(song_log,song_to_search.title,song_to_search.difficulty);
    
#    for song_from_log in song_log:
#        if song_from_log.title == restore_title(song_to_search.title) and song_from_log.difficulty == song_to_search.difficulty:
#            all_plays_of_song.append(song_from_log)
    
    song_date = datetime.strptime(song_to_search.date, "%Y%m%d_%H%M%S")
    
    for song_from_log in all_plays_of_song:
                    
        if not "_" in song_to_search.date or len(song_to_search.date.split('_')) < 2 : 
            print(f'Mallformed song data: {song_to_search.disp()}')
            return True
                
        offset_log_date = datetime.strptime(song_from_log.date, "%Y%m%d_%H%M%S")
        
        # Special case for when I mess around with the TZ because of SDVX
        offset_log_date += timedelta(seconds=-time_offset_seconds)
        
                        
        diference_in_seconds = abs((song_date - offset_log_date).total_seconds())
        diference_in_days = abs((offset_log_date - song_date)).days
       
        if diference_in_days == 0 and diference_in_seconds < 120:
            song_exists = True
            #if song_different_date == True :
            #    print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Found: Log: {song_from_log.date} | Screenshot: {song_to_search.date}\n')
            break;
        elif diference_in_days == 0 and diference_in_seconds >= 120: 
            #print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Difference time: Log: {songLogTime} | Screenshot: {songSSTime} ({diference_in_seconds}s)')
            song_different_date = True
        elif diference_in_days > 0 :
            #print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] Difference date: Log: {songLogDate} | Screenshot: {songSSDate} ({diference_in_days}d)')
            song_different_date = True

    if song_exists == False :
        print(f'[{file_number}] [{song_to_search.title}-{song_to_search.difficulty.upper()}] not found in play log')
        
            
    return song_exists    
    

   
def print_logo():
    print(' _                  __  __           _        ____                   ')
    print('| |    ___   __ _  |  \\/  |_   _ ___(_) ___  / ___| _   _ _ __   ___ ')
    print('| |   / _ \\ / _` | | |\\/| | | | / __| |/ __| \\___ \\| | | | \'_ \\ / __|')
    print('| |__| (_) | (_| | | |  | | |_| \\__ \\ | (__   ___) | |_| | | | | (__ ')
    print('|_____\\___/ \\__, | |_|  |_|\\__,_|___/_|\\___| |____/ \\__, |_| |_|\\___|')
    print('            |___/                                   |___/            ')
    

def remove_songs(song_log):
    print('--------------------------------------------------')
    print('Searching for direct removal songs...')
    for i, song in enumerate(song_log):
        if song.title in direct_removes:
            print(f'Song \'{song.title}\' marked as direct removal. Removing from log...')
            song_log.pop(i)
    
    print('Searching finishing.')
    print('--------------------------------------------------')

def main(song_log_folder, results_folder, rebuild):
    
    print_logo()
    
    if os.path.isdir(results_folder) : 
        root_folder = results_folder
    else :
        print(f'Cannot run log sync: results folder \'{results_folder}\' is not a folder', file=sys.stderr)
        exit(1)
        
    if os.path.isdir(song_log_folder) :       
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S') 
        backup_log_file = 'alllog.pkl.'+timestamp
        print(f'Backuping log file to {backup_log_file}')
        shutil.copyfile(f'{song_log_folder}/alllog.pkl', f'{song_log_folder}/{backup_log_file}')
        
        song_log = load_plays_list(song_log_folder)
        
        if rebuild : 
            print('Deleting and rebuilding play log!')
            song_log.clear()
        else:
            remove_songs(song_log)
                    
    else :
        print(f'Cannot run log sync: alllog folder \'{song_log_folder}\' is not a folder', file=sys.stderr)
        exit(1)
        

    print('Initialising OCR...')
    # When running manually, call in the settings yourself to be able to run from the IDE
    start = datetime(year=2023, month=10, day=15, hour=0)
    gen_summary = GenSummary(start, root_folder + '/sync', 'true', 255, 2)
    
    print(f'Processing {len(os.listdir(root_folder))} files from folder \'{root_folder}\'')
    
    dt_start = datetime.now()

    updated_songs = 0
    processed_files = 0
    results = os.listdir(root_folder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(root_folder, s)))
    
    for play_screenshot_file_name in results:                
        # We ignore files which are a summary and are not png
        if play_screenshot_file_name.find('summary') > 0 :
            continue
        
        if play_screenshot_file_name.find('png') < 0 :
            continue
        
        if not play_screenshot_file_name.startswith("sdvx") :
            continue

        name_splits = play_screenshot_file_name.split("_")
        
        if(len(name_splits) == 3) :
            print(f'Song with no ocr: {play_screenshot_file_name}')
        
        else :
                        
            song_title = ''
            for i in range(1,len(name_splits)) :
                
                # Read all chunks as song title until we hit and difficulty identifier
                if name_splits[i] != 'NOV' and name_splits[i] != 'ADV' and name_splits[i] != 'EXH' and name_splits[i] != 'APPEND':         
                    song_title += name_splits[i] + ' '
                    last_index_of_name = i
                    continue
                else :
                    break;
            try:
            # Set the rest of the data based on offset of the last chunk of the title       
                dif = name_splits[last_index_of_name+1]
            except:
                print(f'Split error on {play_screenshot_file_name}!')
            
            # If the chunk after the difficulty is 'class' we know it's a screenshot of the Skill Analyser mode and we skip that chunk
            if name_splits[last_index_of_name+2] == 'class' :
                last_index_of_name+=1
                
            lamp = name_splits[last_index_of_name+2]
            
            # It can happen that the score is empty and we have a file of type
            # sdvx_プナイプナイたいそう_NOV_failed__20250111_173755
            # In the case, consider the score 0 otherwise things might break later 
            # if the play_date chunks are not assigned correctly
            if name_splits[last_index_of_name+3] == '' :
                score = 0
            else :
                score = name_splits[last_index_of_name+3]
            
            try:    
                play_date = name_splits[last_index_of_name+4]+'_'+name_splits[last_index_of_name+5]
            except:
                print(f'Split error on {play_screenshot_file_name}!')
                    
            play_date = play_date.removesuffix('.png')
                
        #print(f'Read from file: {song_title} / {dif} / {lamp} / {score} / {play_date}')
        
        img = Image.open(os.path.abspath(f'{root_folder}/{play_screenshot_file_name}'))
        score_from_image = gen_summary.get_score(img)                
        
#        if song_without_ocr : 
#            gen_summary.ocr_only_jacket(img)

        if song_title != '':
            
            if is_special_title(song_title) :                
                for i in range(0,len(song_log)) :                    
                    if song_log[i].title == song_title.strip() :
                        song_log.pop(i)
                        print(f'Removed incorrect song with title {song_title} from play log.')
                        break                                                                    
                        
            song_title = restore_title(song_title)
            
            play_score = score_from_image[0];
            previous_score = score_from_image[1]
            
            song_from_screenshot = OnePlayData(song_title, play_score, previous_score, lamp, dif.lower(), play_date.removesuffix('.png_'))

            # If the song is not in the long, with a tolerance of 120 seconds, add it to the log                
            if not is_song_in_log(song_log, song_from_screenshot,processed_files):
                print(f'[{processed_files}] [{song_from_screenshot.title}-{song_from_screenshot.difficulty.upper()}] Adding to log with date {song_from_screenshot.date}\n')
                song_log.append(song_from_screenshot)
                updated_songs += 1
        
        processed_files += 1
        if processed_files % 100 == 0:
            print(f'{processed_files} files processed...')
    
    dt_end = datetime.now()
    duration = dt_end - dt_start
    print(f'Update song log with {updated_songs} songs out of {processed_files} valid files in {round(duration.total_seconds(),2)}s')
    save(song_log, song_log_folder)
    
    
def find_song_rating(song_from_log, song_list):
    
    rating = 0
    
    restored_song_title = restore_title(song_from_log.title)
    
    # Find the numeric value of the song rating based on it's difficulty category
    song = song_list['titles'].get(restored_song_title,None)
    
    if song is not None :
        if song_from_log.difficulty == 'nov' : 
            rating = song[3]
        elif song_from_log.difficulty == 'adv' :
            rating = song[4]
        elif song_from_log.difficulty == 'exh' :
            rating = song[5]
        else :
            rating = song[6]
                
    if rating == 0 :
        print(f'[{restored_song_title}-{song_from_log.difficulty.upper()}] Could not find song in song list for rating. Searching for direct overrides...')
        override = direct_overides.get(restored_song_title)
        
        if override is not None:
            for override_rating in override :
                if override_rating.split(":")[0].lower() == song_from_log.difficulty.lower() :
                   rating = override_rating.split(":")[1]
                   print(f'[{restored_song_title}-{song_from_log.difficulty.upper()}] Direct override found with rating {rating}')
                   break
               
            if rating == 0 :
                print(f'[{restored_song_title}-{song_from_log.difficulty.upper()}] No rating found for {song_from_log.difficulty.upper()}')
               
        else :
            print(f'[{restored_song_title}-{song_from_log.difficulty.upper()}] not found in direct override')

                            
        
    return str(rating)
    
def dump(song_log_folder, song_list_folder):
    
    song_log = load_plays_list(song_log_folder)
    song_list = load_song_list(song_list_folder)
    song_log.sort(key=lambda s: s.date)
    
    song_list_element = ET.Element("song_list")
    xml_tree = ET.ElementTree(song_list_element)
    plays={}
    songs={}
    
    print(f'Dumping {len(song_log)} song plays to XML...')
    for song_from_log in song_log:
        
        title = restore_title(song_from_log.title)
        
        rating = find_song_rating(song_from_log, song_list)
        song_hash_play = str(hash(title+"_"+song_from_log.difficulty+"_"+rating))
        song_hash_title = str(hash(title))
                
        existing_play_node = plays.get(song_hash_play,None)
        existing_song_node = songs.get(song_hash_title,None)
        
        #Format the date to more similar to ISO
        song_date = datetime.strptime(song_from_log.date, '%Y%m%d_%H%M%S')
        formatted_date = song_date.strftime("%Y-%m-%d %H:%M:%S")
        
        if existing_song_node is not None :
            if existing_play_node is not None : 
                ET.SubElement(existing_play_node,"play",score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
            else : 
                plays_node = ET.SubElement(existing_song_node,"plays", difficulty=song_from_log.difficulty, rating=rating)
                ET.SubElement(plays_node,"play",score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
                plays[song_hash_play] = plays_node                            
        # If we already added this song, create new "play" entry under the same song and difficulty / rating
#        if existing_play_node is not None:       
#            ET.SubElement(existing_play_node,"play",score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)        
        else :
            song_node = ET.SubElement(song_list_element, "song", title=title)
            plays_node = ET.SubElement(song_node,"plays", difficulty=song_from_log.difficulty, rating=rating)
            ET.SubElement(plays_node,"play",score=str(song_from_log.cur_score), lamp=song_from_log.lamp, date=formatted_date)
            plays[song_hash_play] = plays_node
            songs[song_hash_title] = song_node
        
        
    print(f'Writing XML to {song_log_folder}/played_songs.xml')
    ET.indent(xml_tree, space="\t", level=0)
    xml_tree.write(song_log_folder+"played_songs.xml",encoding="UTF-8",xml_declaration=True)
        
        
if __name__ == '__main__':
    
    parser = argparse.ArgumentParser(description='Reads the sdvx results folders and re-inserts missing songs into the alllog.pkl')
    parser.add_argument('--songLog', required=True, help='The directory containing the alllog (alllog.pkl) file')
    parser.add_argument('--results', required=True, help='The directory containing the result screenshots')
    parser.add_argument('--dump', required=False, help='Dumps the alllog.pkl into an xml file', action='store_true')
    parser.add_argument('--songList', required=False, help='The directory containing the song list (musiclist.pkl) file, only used with the --dump option')
    parser.add_argument('--rebuildSongLog', required=False, help='Rebuilds the whole song log from the result screenshots', action='store_true')
    
    args = parser.parse_args()
    main(args.songLog, args.results,args.rebuildSongLog)
    
    if args.dump :
        dump(args.songLog, args.songList)
    
    

