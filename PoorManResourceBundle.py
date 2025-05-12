
bundles = {
    'en': {
        'button.rescan': 'Colorize file list (heavy)',
        'button.merge.pkl': 'Merge External PKL',
        'button.send.pkl': 'Send PKL',
        'button.resgister': 'Register Song',
        
        'text.difficulty': 'difficulty',
        'text.registered': 'Registered',
        'text.music_score': 'Music Score',
        
        'window.title': 'SDVX helper - OCR undetected song reporting tool',
        
        'message.coloring':'File list is being colored according to OCR results. Please wait.',
        'message.coloring.complete':'Coloring completed.',
        'message.song.registered':'The song has not been registered. All score information will be registered.',
        'message.song.not.registered':'This song is not registered in the song title database. Please select the song and then click "Register Song".',
        'message.song.already.registered':'The song has already been registered.',
        'message.hash.fixed':'The hash has been fixed.',
        'message.non.result.images':'(Files that are not result images)',
        'message.error.file.not.found':'Error! File not found',
        'message.error.cannot.obtain':'Unable to obtain difficulty or hash value',
        'message.error.no.title':'No song title entered',
        'message.screenshot.saved':'Screenshot saved',
        'message.on.detect':'Detect the song selection screen',
        
        'log.song.registered':'Not registered. Register all score information.',
        'log.song.already.registered':'The song itself has been registered.',
        'log.hash.fixed':'Only fixes the hash.',
        'log.filename.exists':'Skip as the file name already exists.',
        'log.file.not.found':'File not found, skipping.',
        
        'webhook.number.added.scores':'Number of added scores',
        'webhook.number.added.songs':'Number of songs'
        
    },
    'ja': {
        'button.rescan': 'ファイル一覧に色付け(重いです)',
        'button.merge.pkl': '外部pklのマージ',
        'button.send.pkl': 'pklを送信',
        'button.resgister': '曲登録',
        
        'text.difficulty': '難易度',
        'text.registered': '登録済',
        'text.music_score': '譜面',
        
        'window.title': 'SDVX helper - OCR未検出曲報告ツール',
        
        'message.coloring':'ファイル一覧をOCR結果に応じて色付け中。しばらくお待ちください。',
        'message.coloring.complete':'色付けを完了しました。',
        'message.song.registered':'曲が未登録。全譜面の情報を登録します。',
        'message.song.not.registered':'曲名DBに登録されていません。曲を選択してから曲登録を押してもらえると喜びます。',
        'message.song.already.registered':'曲自体は登録済み。',
        'message.hash.fixed':'のhashを修正しました。',
        'message.non.result.images':'(リザルト画像ではないファイル)',
        'message.error.file.not.found':'error! ファイル見つかりません',
        'message.error.cannot.obtain':'難易度またはハッシュ値が取得できません',
        'message.error.no.title':'曲名が入力されていません',
        'message.screenshot.saved':'スクリーンショットを保存しました',
        'message.on.detect':'曲決定画面を検出',
        
        'log.song.registered':'登録されていません。全譜面の情報を登録します。',
        'log.song.already.registered':'曲自体の登録はされています。この譜面',
        'log.hash.fixed':'のみhashを修正します。',
        'log.filename.exists':'既に存在するファイル名なのでskip。',
        'log.file.not.found':'ファイルが見つかりません。スキップします。',
        
        'webhook.number.added.scores':'追加した譜面数',
        'webhook.number.added.songs':'曲数'
    }
}


class PoorManResourceBundle :
    bundle = None
    
    def __init__(self, locale:str='ja'):
        
        self.bundle = bundles.get(locale.lower());
        
    def getText(self, key):
        if not self.bundle == None :
            return self.bundle.get(key)
        else :
            raise 'Bundle not initialised'


if __name__ == '__main__':
    a = PoorManResourceBundle(locale = 'ja')        
        
        