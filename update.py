import PySimpleGUI as sg
import os, re, sys
import urllib.request
from typing import Optional
import zipfile
import shutil
from glob import glob
from bs4 import BeautifulSoup
import urllib, requests
import threading
import logging, logging.handlers
import traceback
from pathlib import Path
import sdvx_utils
from poor_man_resource_bundle import *

os.makedirs('log', exist_ok=True)
logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)
hdl = logging.handlers.RotatingFileHandler(
    f'log/{os.path.basename(__file__).split(".")[0]}.log',
    encoding='utf-8',
    maxBytes=1024*1024*2,
    backupCount=1,
)
hdl.setLevel(logging.DEBUG)
hdl_formatter = logging.Formatter('%(asctime)s %(filename)s:%(lineno)5d %(funcName)s() [%(levelname)s] %(message)s')
hdl.setFormatter(hdl_formatter)
logger.addHandler(hdl)

sg.theme('SystemDefault1')

SWVER = sdvx_utils.get_version("updater")

class Updater:
    
    def __init__(self):
        self.default_locale = 'EN'
        self.bundle = PoorManResourceBundle(self.default_locale)
        self.i18n = self.bundle.get_text
        self.ico=self.ico_path('icon.ico')
    
    def update_from_url(self, url):
        filename = 'tmp/tmp.zip'
        self.window['txt_info'].update(f'{self.i18n("message.updater.downloading")}')

        def _progress(block_count: int, block_size: int, total_size: int):
            percent = int((block_size*block_count*100)/total_size)
            self.window['prog'].update(percent)

        # zipファイルのDL
        logger.debug('now downloading...')
        os.makedirs('tmp', exist_ok=True)
        urllib.request.urlretrieve(url, filename, _progress)

        # zipファイルの解凍
        logger.debug('now extracting...')
        self.window['txt_info'].update(f'{self.i18n("message.updater.unziping")}')
        shutil.unpack_archive(filename, 'tmp')

        zp = zipfile.ZipFile(filename, 'r')

        target_dir = '.'
        logger.debug('now moving...')
        p = Path('tmp/sdvx_helper')
        failed_list = []
        for f in p.iterdir():
            if f.is_dir():
                subdir=f.relative_to('tmp/sdvx_helper')
                os.makedirs(subdir, exist_ok=True)
        for f in p.glob('**/*.*'):
            try:
                base = str(f.relative_to('tmp/sdvx_helper'))
                logger.debug(f)
                shutil.move(str(f), target_dir+'/'+base)
            except Exception:
                if 'update.exe' not in str(f):
                    failed_list.append(f)
                logger.debug(f"error! ({f})")
                logger.debug(traceback.format_exc())
        shutil.rmtree('tmp/sdvx_helper')
        out = ''
        if len(failed_list) > 0:
            out = f'{self.i18n("message.updater.unziping.failed")}: '
            out += '\n'.join(failed_list)

        self.window.write_event_value('-FINISH-', out)

    # icon用
    def ico_path(self, relative_path):
        try:
            base_path = sys._MEIPASS
        except Exception:
            base_path = os.path.abspath(".")
        return os.path.join(base_path, relative_path)

    def gui(self):
        layout = [
            [sg.Text('', key='txt_info')],
            [sg.ProgressBar(100, key='prog', size=(30, 15))],
        ]
        self.window = sg.Window('infdc update manager', layout, grab_anywhere=True,return_keyboard_events=True,resizable=False,finalize=True,enable_close_attempted_event=True,icon=self.ico)

    def main(self, url):
        self.gui()
        th = threading.Thread(target=self.update_from_url, args=(url,), daemon=True)
        th.start()
        while True:
            ev,val = self.window.read()
            if ev in (sg.WIN_CLOSED, 'Escape:27', '-WINDOW CLOSE ATTEMPTED-'):
                value = sg.popup_yes_no(f'{self.i18n("popup.updater.cancel")}', icon=self.ico,title=f'{app.i18n("window.update.title",SWVER)}')
                if value == 'Yes':
                    break
            elif ev == '-FINISH-':
                msg = f' {self.i18n("popup.updater.complete")} ' + val[ev]
                sg.popup_ok(msg, icon=self.ico)
                break

if __name__ == '__main__':
    app = Updater()
    ver = sdvx_utils.get_latest_version()
    helper_version = sdvx_utils.get_version("helper")
    url = f'https://github.com/Throdax/sdvx_helper/releases/download/{ver}/sdvx_helper_en_all.zip'
    if type(ver) != str:
        sg.popup_error(f'{app.i18n("popup.updater.noRepo")}',icon=app.ico,title=f'{app.i18n("window.update.title",SWVER)}')
    elif re.findall(r'\d+', helper_version) == re.findall(r'\d+', ver):
        print(f'{app.i18n("popup.updater.alreadyLatest")}')
        sg.popup_ok(f'{app.i18n("popup.updater.alreadyLatest")}',icon=app.ico,title=f'{app.i18n("window.update.title",SWVER)}')
    else:
        value = sg.popup_ok_cancel(f'{app.i18n("popup.updater.newVersion",helper_version,ver)}',icon=app.ico,title=f'{app.i18n("window.update.title",SWVER)}')
        if value == 'OK':
            app.main(url)

