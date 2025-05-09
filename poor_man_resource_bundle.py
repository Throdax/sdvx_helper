
bundles = {
    'en': {
        'button.rescan': 'Colorize file list (heavy)',
        'button.merge.pkl': 'Merge External PKL',
        'button.send.pkl': 'Send PKL',
        'button.resgister': 'Register Song',
        'text.difficulty': 'difficulty',
        'text.registered': 'Registered',
        'text.music_score': 'Music Score',
        'window.title': 'SDVX helper - OCR undetected song reporting tool'
        
    },
    'ja': {
        'button.rescan': 'ファイル一覧に色付け(重いです)',
        'button.merge.pkl': '外部pklのマージ',
        'button.send.pkl': 'pklを送信',
        'button.resgister': '曲登録',
        'text.difficulty': '難易度',
        'text.registered': '登録済',
        'text.music_score': '譜面',
        'window.title': 'SDVX helper - OCR未検出曲報告ツール'
    }
}


class PoorManResourceBundle :
    bundle = None
    
    def __init__(self, locale:str='ja'):
        
        self.bundle = bundles.get(locale);
        
    def getText(self, key):
        if not self.bundle == None :
            return self.bundle.get(key)
        else :
            raise 'Bundle not initialised'


if __name__ == '__main__':
    a = PoorManResourceBundle(locale = 'ja')        
        
        