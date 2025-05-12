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

specialTitles = {
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
        '侵蝕コード 666 -今日ちょっと指 (略-':'侵蝕コード : 666 -今日ちょっと指 (略-',
        '隅田川夏恋歌IO Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        '隅田川純恋歌IO Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        '隅田川純恋歌I/O Angel mix':'隅田川夏恋歌 (I/O Angel mix)',
        'ハナビラリンクス':'ハナビラ:リンクス',
        'Heartache  心の痛み':'Heartache / 心の痛み',
        'Chantilly Fille':'゜*。Chantilly Fille。*°',
        'infinite youniverse':'infinite:youniverse'
    }

directOverides = {
    'Pure Evil': {'NOV:5','ADV:12','EXH:17'},
    'チルノのパーフェクトさんすう教室　⑨周年バージョン':{'NOV:5','ADV:11','EXH:13','APPEND:16'},
    'チルノのパーフェクトさんすう教室 ⑨周年バージョン':{'NOV:5','ADV:11','EXH:13','APPEND:16'},
    'グレイスちゃんの超～絶!!グラビティ講座w':{'APPEND:1'},
    'マキシマ先生の満開!!ヘヴンリー講座♥':{'APPEND:1'},
    'エクシード仮面ちゃんのちょっと一線をえくしーどしたEXCEED講座':{'APPEND:1'}
    }

ignoredNames = {
    'Help me, CODYYYYYY!!'
}

sdvx_logger = SDVXLogger("Throdax")
time_offset_seconds = 0

def restoreTitle(songTitle):       
    return specialTitles.get(songTitle.strip(),songTitle.strip())

def isSpecialTitle(songTitle):
    return songTitle.strip() in specialTitles

def loadSongList(songList):
    ret = None
    with open(f'{songList}/musiclist.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret

def loadPlaysList(allogFolder):
    ret = None
    with open(f'{allogFolder}/alllog.pkl', 'rb') as f:
        ret = pickle.load(f)
    return ret


def save(dat:dict, allogFolder):
    with open(f'{allogFolder}/alllog.pkl', 'wb') as f:
        pickle.dump(dat, f)
        
def getSongFromLog(songLog, songTitle, dificulty):
    
    allPlaysOfSong = []
    
    for songFromLog in songLog:
        if songFromLog.title == songTitle and songFromLog.difficulty == dificulty:
            allPlaysOfSong.append(songFromLog)
            
    return allPlaysOfSong
    

        
def isSongInLog(songLog, songToSearch,fileNumber):
    
    if not ignoredNames.get(songToSearch.title) == None :
        return true
        
    songExists = False
    songDifferentDate = False
    
    allPlaysOfSong = getSongFromLog(songLog,songToSearch.title,songToSearch.difficulty);
    
#    for songFromLog in songLog:
#        if songFromLog.title == restoreTitle(songToSearch.title) and songFromLog.difficulty == songToSearch.difficulty:
#            allPlaysOfSong.append(songFromLog)
    
    songDate = datetime.strptime(songToSearch.date, "%Y%m%d_%H%M%S")
    
    for songFromLog in allPlaysOfSong:
                    
        if not "_" in songToSearch.date or len(songToSearch.date.split('_')) < 2 : 
            print(f'Mallformed song data: {songToSearch.disp()}')
            return True
                
        offsetLogDate = datetime.strptime(songFromLog.date, "%Y%m%d_%H%M%S")
        
        # Special case for when I mess around with the TZ because of SDVX
        offsetLogDate += timedelta(seconds=-time_offset_seconds)
        
                        
        diferenceInSeconds = abs((songDate - offsetLogDate).total_seconds())
        diferenceInDays = abs((offsetLogDate - songDate)).days
       
        if diferenceInDays == 0 and diferenceInSeconds < 120:
            songExists = True
            #if songDifferentDate == True :
            #    print(f'[{fileNumber}] [{songToSearch.title}-{songToSearch.difficulty.upper()}] Found: Log: {songFromLog.date} | Screenshot: {songToSearch.date}\n')
            break;
        elif diferenceInDays == 0 and diferenceInSeconds >= 120: 
            #print(f'[{fileNumber}] [{songToSearch.title}-{songToSearch.difficulty.upper()}] Difference time: Log: {songLogTime} | Screenshot: {songSSTime} ({diferenceInSeconds}s)')
            songDifferentDate = True
        elif diferenceInDays > 0 :
            #print(f'[{fileNumber}] [{songToSearch.title}-{songToSearch.difficulty.upper()}] Difference date: Log: {songLogDate} | Screenshot: {songSSDate} ({diferenceInDays}d)')
            songDifferentDate = True

    if songExists == False :
        print(f'[{fileNumber}] [{songToSearch.title}-{songToSearch.difficulty.upper()}] not found in play log')
        
            
    return songExists    
    

# TODO: Find a way to extract the data from a result screenshot without data in the filename
def parse_unparsed_results_screen (resultsFilename):
    img = Image.open(os.path.abspath(f'{rootFolder}/{playScreenshotFileName}'))
    parts = genSummary.cut_result_parts(img)
    genSummary.ocr()
    dif = genSummary.difficulty
    
def print_logo():
    print(' _                  __  __           _        ____                   ')
    print('| |    ___   __ _  |  \\/  |_   _ ___(_) ___  / ___| _   _ _ __   ___ ')
    print('| |   / _ \\ / _` | | |\\/| | | | / __| |/ __| \\___ \\| | | | \'_ \\ / __|')
    print('| |__| (_) | (_| | | |  | | |_| \\__ \\ | (__   ___) | |_| | | | | (__ ')
    print('|_____\\___/ \\__, | |_|  |_|\\__,_|___/_|\\___| |____/ \\__, |_| |_|\\___|')
    print('            |___/                                   |___/            ')
    
def main(songLogFolder, resultsFolder, rebuild):
    
    print_logo()
    
    if os.path.isdir(resultsFolder) : 
        rootFolder = resultsFolder
    else :
        print(f'Cannot run log sync: results folder \'{resultsFolder}\' is not a folder', file=sys.stderr)
        exit(1)
        
    if os.path.isdir(songLogFolder) :       
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S') 
        backupLogFile = 'alllog.pkl.'+timestamp
        print(f'Backuping log file to {backupLogFile}')
        shutil.copyfile(f'{songLogFolder}/alllog.pkl', f'{songLogFolder}/{backupLogFile}')
        
        songLog = loadPlaysList(songLogFolder)
        
        if rebuild : 
            print(f'Deleting and rebuilding play log!')
            songLog.clear()
    else :
        print(f'Cannot run log sync: alllog folder \'{songLogFolder}\' is not a folder', file=sys.stderr)
        exit(1)
        

    print('Initialising OCR...')
    # When running manually, call in the settings yourself to be able to run from the IDE
    start = datetime(year=2023, month=10, day=15, hour=0)
    genSummary = GenSummary(start, rootFolder + '/sync', 'true', 255, 2)
    
    print(f'Processing {len(os.listdir(rootFolder))} files from folder \'{rootFolder}\'')
    
    dtStart = datetime.now()

    updatedSongs = 0
    processedFiles = 0
    results = os.listdir(rootFolder)
    results.sort(key=lambda s: os.path.getctime(os.path.join(rootFolder, s)))
    
    for playScreenshotFileName in results:                
        # We ignore files which are a summary and are not png
        if playScreenshotFileName.find('summary') > 0 :
            continue
        
        if playScreenshotFileName.find('png') < 0 :
            continue
        
        if not playScreenshotFileName.startswith("sdvx") :
            continue

        nameSplits = playScreenshotFileName.split("_")
        
        if(len(nameSplits) == 3) :
            songWithoutOCR = True
            print(f'Song with no ocr: {playScreenshotFileName}')
        
        else :
                        
            songTitle = ''
            for i in range(1,len(nameSplits)) :
                
                # Read all chunks as song title until we hit and difficulty identifier
                if nameSplits[i] != 'NOV' and nameSplits[i] != 'ADV' and nameSplits[i] != 'EXH' and nameSplits[i] != 'APPEND':         
                    songTitle += nameSplits[i] + ' '
                    lastIndexOfName = i
                    continue
                else :
                    break;
            try:
            # Set the rest of the data based on offset of the last chunk of the title       
                dif = nameSplits[lastIndexOfName+1]
            except:
                print(f'Split error on {playScreenshotFileName}!')
            
            # If the chunk after the difficulty is 'class' we know it's a screenshot of the Skill Analyser mode and we skip that chunk
            if nameSplits[lastIndexOfName+2] == 'class' :
                lastIndexOfName+=1
                
            lamp = nameSplits[lastIndexOfName+2]
            
            # It can happen that the score is empty and we have a file of type
            # sdvx_プナイプナイたいそう_NOV_failed__20250111_173755
            # In the case, consider the score 0 otherwise things might break later 
            # if the playDate chunks are not assigned correctly
            if nameSplits[lastIndexOfName+3] == '' :
                score = 0
            else :
                score = nameSplits[lastIndexOfName+3]
            
            try:    
                playDate = nameSplits[lastIndexOfName+4]+'_'+nameSplits[lastIndexOfName+5]
            except:
                print(f'Split error on {playScreenshotFileName}!')
                    
            playDate = playDate.removesuffix('.png')
                
        #print(f'Read from file: {songTitle} / {dif} / {lamp} / {score} / {playDate}')
        
        img = Image.open(os.path.abspath(f'{rootFolder}/{playScreenshotFileName}'))
        scoreFromImage = genSummary.get_score(img)                
        
#        if songWithoutOCR : 
#            genSummary.ocr_only_jacket(img)

        if songTitle != '':
            
            if isSpecialTitle(songTitle) :                
                for i in range(0,len(songLog)) :                    
                    if songLog[i].title == songTitle.strip() :
                        songLog.pop(i)
                        print(f'Removed incorrect song with title {songTitle} from play log.')
                        break                                                                    
                        
            songTitle = restoreTitle(songTitle)
            
            playScore = scoreFromImage[0];
            previousScore = scoreFromImage[1]
            
#            songPlays = getSongFromLog(songLog, songTitle, dif.lower())
#            if len(songPlays) > 0 :
#                
#                lastPlay = None
#
#                for songPlay in songPlays :
#                    if lastPlay == None :
#                        lastPlay = songPlay
#                    elif lastPlay.date > songPlay.date :
#                        lasPlay = songPlay
#                
#                previousScore = lastPlay.pre_Scor
            
            songFromScreenshot = OnePlayData(songTitle, playScore, previousScore, lamp, dif.lower(), playDate.removesuffix('.png_'))

            # If the song is not in the long, with a tolerance of 120 seconds, add it to the log                
            if not isSongInLog(songLog, songFromScreenshot,processedFiles):
                print(f'[{processedFiles}] [{songFromScreenshot.title}-{songFromScreenshot.difficulty.upper()}] Adding to log with date {songFromScreenshot.date}\n')
                songLog.append(songFromScreenshot)
                updatedSongs += 1
        
        processedFiles += 1
        if processedFiles % 100 == 0:
            print(f'{processedFiles} files processed...')
    
    dtEnd = datetime.now()
    duration = dtEnd - dtStart
    print(f'Update song log with {updatedSongs} songs out of {processedFiles} valid files in {round(duration.total_seconds(),2)}s')
    save(songLog, songLogFolder)
    
    
def findSongRating(songFromLog, songList):
    
    rating = 0
    
    restoredSongTitle = restoreTitle(songFromLog.title)
    
    # Find the numeric value of the song rating based on it's difficulty category
    song = songList['titles'].get(restoredSongTitle,None)
    
    if song is not None :
        if songFromLog.difficulty == 'nov' : 
            rating = song[3]
        elif songFromLog.difficulty == 'adv' :
            rating = song[4]
        elif songFromLog.difficulty == 'exh' :
            rating = song[5]
        else :
            rating = song[6]
                
    if rating == 0 :
        print(f'[{restoredSongTitle}-{songFromLog.difficulty.upper()}] Could not find song in song list for rating. Searching for direct overrides...')
        override = directOverides.get(restoredSongTitle)
        
        if override is not None:
            for overrideRating in override :
                if overrideRating.split(":")[0].lower() == songFromLog.difficulty.lower() :
                   rating = overrideRating.split(":")[1]
                   print(f'[{restoredSongTitle}-{songFromLog.difficulty.upper()}] Direct override found with rating {rating}')
                   break
               
            if rating == 0 :
                print(f'[{restoredSongTitle}-{songFromLog.difficulty.upper()}] No rating found for {songFromLog.difficulty.upper()}')
               
        else :
            print(f'[{restoredSongTitle}-{songFromLog.difficulty.upper()}] not found in direct override')

                            
        
    return str(rating)
    
def dump(songLogFolder, songListFolder):
    
    songLog = loadPlaysList(songLogFolder)
    songList = loadSongList(songListFolder)
    songLog.sort(key=lambda s: s.date)
    
    songListElement = ET.Element("songList")
    xmlTree = ET.ElementTree(songListElement)
    plays={}
    songs={}
    
    print(f'Dumping {len(songLog)} song plays to XML...')
    for songFromLog in songLog:
        
        title = restoreTitle(songFromLog.title)
        
        rating = findSongRating(songFromLog, songList)
        songHashPlay = str(hash(title+"_"+songFromLog.difficulty+"_"+rating))
        songHashTitle = str(hash(title))
                
        existingPlayNode = plays.get(songHashPlay,None)
        existingSongNode = songs.get(songHashTitle,None)
        
        #Format the date to more similar to ISO
        songDate = datetime.strptime(songFromLog.date, '%Y%m%d_%H%M%S')
        formatted_date = songDate.strftime("%Y-%m-%d %H:%M:%S")
        
        if existingSongNode is not None :
            if existingPlayNode is not None : 
                ET.SubElement(existingPlayNode,"play",score=str(songFromLog.cur_score), lamp=songFromLog.lamp, date=formatted_date)
            else : 
                playsNode = ET.SubElement(existingSongNode,"plays", difficulty=songFromLog.difficulty, rating=rating)
                ET.SubElement(playsNode,"play",score=str(songFromLog.cur_score), lamp=songFromLog.lamp, date=formatted_date)
                plays[songHashPlay] = playsNode                            
        # If we already added this song, create new "play" entry under the same song and difficulty / rating
#        if existingPlayNode is not None:       
#            ET.SubElement(existingPlayNode,"play",score=str(songFromLog.cur_score), lamp=songFromLog.lamp, date=formatted_date)        
        else :
            songNode = ET.SubElement(songListElement, "song", title=title)
            playsNode = ET.SubElement(songNode,"plays", difficulty=songFromLog.difficulty, rating=rating)
            ET.SubElement(playsNode,"play",score=str(songFromLog.cur_score), lamp=songFromLog.lamp, date=formatted_date)
            plays[songHashPlay] = playsNode
            songs[songHashTitle] = songNode
        
        
    print(f'Writing XML to {songLogFolder}/played_songs.xml')
    ET.indent(xmlTree, space="\t", level=0)
    xmlTree.write(songLogFolder+"/played_songs.xml",encoding="UTF-8",xml_declaration=True)
        
        
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
    
    

