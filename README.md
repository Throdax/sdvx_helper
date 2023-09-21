# sdvx_helper
コナステ版SOUND VOLTEX用の配信補助ツールです。  
OBSでの配信を想定しています。  
譜面付近のみを切り取ったレイアウトでも、曲情報を見やすく表示することができます。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/d031d2e8-e1f6-439d-b64b-a5a9baef8638)

また、リザルト画像の自動保存や、保存したリザルト画像からプレーログ画像の作成も行うことができます。  
プレーログ画像はリザルト画像撮影のたびに自動更新されます。  
F6キーを押すことで、縦向きに直した画像を保存する機能もあります。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/155aafc4-772b-43d0-bd94-3a38004ddac9)

さらに、ゲーム内の各シーン(選曲画面、プレー画面、リザルト画面)でOBS上のソースに対して表示・非表示を切り替えたり、  
別のシーンに移行したりできます。  
(例: プレー画面だけ手元カメラを表示、リザルト画面だけVTuberのアバターを消す、等)

# インストール方法
[Releaseページ](https://github.com/dj-kata/sdvx_helper/releases)の一番上にあるsdvx_helper.zipをダウンロードし、好きなフォルダ(デスクトップ以外)に解凍してください。  
sdvx_helper.exeをクリックすると実行できます。

# 設定方法
## 1. OBS(28以降)でwebsocketが使えるように設定する。
OBSwebsocketについては、インストールされていない場合は[ここ](https://github.com/obsproject/obs-websocket/releases)から最新のalphaってついてないバージョンの(～Windows-Installer.exe)をDLしてインストールしてください。  
OBSのメニューバー内ツール→WebSocketサーバ設定で以下のように設定してあればOK。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/5ee16668-8b8a-4b13-91c8-8a3fff312e5d)

## 2. sdvx_helper.exeを実行し、メニューバーから設定を開く
## 3. 1で設定したポート番号とパスワードを入力する。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/48cdc815-3259-4ede-8f40-6263259fe4d8)
## 4. コナステ版SoundVoltexの設定画面で指定している画面の向きを選択する
回転しないレイアウトには現在未対応となります。  
SDVX側の設定と本ツールの設定は以下のように対応しています。  
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/888c9ea4-f4e6-47aa-bb20-bbe21938cc13)

## 5. 設定画面を閉じる
## 6. メニューバーからOBS制御設定を開く
## 7. OBS配信や録画で使うシーン名を選択し、ゲーム画面のキャプチャに使うソース名を選択してから、ゲーム画面の横にあるsetを押す。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/4252eb9a-7202-4f26-931f-397b14354ee9)
## 8. OBSにout\nowplaying.htmlをドラッグ&ドロップする。
nowplaying.htmlはソースをダブルクリックして幅820,高さ900に設定すると余白がいい感じになります。  
また、OBS制御設定からプレー中のみ表示、みたいなこともできます。
![image](https://github.com/dj-kata/sdvx_helper/assets/61326119/0b8e3b89-7ac4-4048-88f2-f56cbd97374a)

ソース名がnowplaying.htmlまたはnowplayingでないと自動リロードされないので注意。  
outフォルダ内の画像を直接OBS上に配置する場合は自動でリロードされるようです。

## 9. OBSにout\summary_small.pngをドラッグ&ドロップする。
30曲分の履歴を表示するように作っていますが、曲数を減らしたい場合はAlt+マウスドラッグでトリミングしてください。  
本機能を使うためには以下2点に注意する必要があります。
1. 設定画面でリザルトの保存先フォルダを設定しておく
2. 起動してからリザルトが保存されている

2．について、全てのリザルトを自動保存する機能があるので、そちらを有効にすることを推奨します。  
サマリ画像生成時にはランクDのリザルトのみ弾く機能もあります。(今後もう少し拡張するかも)

# 使い方
上記設定ができていれば、OBS配信や録画を行う際に起動しておくだけでOKです。  
F6キーを押すと指定したフォルダにキャプチャ画像を正しい向きで保存することができます。

