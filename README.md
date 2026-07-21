# Intro 
I've changed the images from the english manual to the english ones provided by my fork. The rest for the most part remains untouched from the original dj-kata english manual and only the "Includes" section has been updated.

**Please do not bother dj-kata with issues you have encountered with this fork** but instead feel free to open an [issue](https://github.com/Throdax/sdvx_helper/issues) or a [pull-request](https://github.com/Throdax/sdvx_helper/pulls) if you have translation language files other than Japanese and English.

I will continue to update this README with the new features I've added in this fork.

You can find the uploads of my play sessions and SDVX Helper - Puni Edition in action on my [Youtube channel](https://www.youtube.com/playlist?list=PL8wHeBFaeU8i4D0MueTlCZKHkWgqBq89-) (fair warning, I'm not that good)

# Manual 
dj-kata created a simple English version of the manual.

説明書の日本語版は[こちら](https://github.com/Throdax/sdvx_helper#README_JP.md)。

# SDVX Helper - Puni Edition
SDVX Helper - Puni Edition is an application for SOUND VOLTEX EXCEED GEAR コナステ.  
It aims for streaming with Open Broadcaster Software (OBS).  
Even when the chart area is enlarged on your streaming layout,  current music information can be displayed clearly with this application.
![image](https://github.com/user-attachments/assets/287f6de5-767f-4c04-b328-6e429bdccbb3)

With this application, you can save result images automatically, and can 
generate a playlog image using them.
Each time a result image is captured, the playlog image is updated.
You can also save a screenshot on pressing F6 key.
![image](https://github.com/user-attachments/assets/66483463-3b6f-43a0-8a83-6e95ef55eebd)

Additionally, you can configure automatic control settings for OBS.
You can show or hide sources and switch scenes
for each in-game scene(play, result, select).
For example, you can display camera footage showing your hands only
during playing songs, and hide the VTuber avatar only on result
scene.

## The principles of this application
Just to clarify, the processing of this application is not related to reverse engineering.
The application periodically captures the game screen and determines the type of screen through image processing.
This application obtains the capture via OBS websocket,
which helps to minimize the load on the PC.

## The environment for verification
This application has been tested in the following environment.
```
OS: Windows10 64bit(22H2)
CPU: Intel Core i7-14700F
GPU: NVIDIA RTX3060 Ti
Antivirus software: Windows Defender
OBS: 31.0.3
```

Make sure you have OBS version 28 or newer for the latest WebSocket API. 
Even AMD CPUs should work fine (it's pretty stable via WebSocket). 
Just a heads up, your antivirus might block communication with OBS through TCP port 4444.

# Includes

|file name|description|
|-|-|
|sdvx_helper.exe|Execution file for SDVX Helper - Puni Edition|
|update.exe|Execution file for auto-updating this application|
|manage_score.exe|A standalone tool to manage your play log|
|ocr_reporter.exe|A standalone tool to help identify unrecognized songs that have been played|
|play_log_sync.exe|A standalone tool to help sync result screenshots back to the play log (useful after playing for unrecognized songs)|
|version.properties|The version information|
|README.txt|Concise instruction manual|
|out/|The destination directory for outputting music information, playlog, etc...|
|out/nowplaying.html|An HTML file for displaying song information. You can use it by dragging and dropping it into OBS.|
|out/summary_full.png|A play log images of the day(detailed)|
|out/summary_small.png|A play log images of the day(summary)|
|resources/images|Set of files for recognition|
|resources/i18n|Language files used to set the applications in the desired languages (feel free to contribute new translations via pull requests)|

# New features in the Puni Edition
This fork extends the original application with a number of quality-of-life improvements.

## Discord Rich Presence
SDVX Helper - Puni Edition can update your Discord status automatically while you play.
It shows the song currently being played, your Volforce, and the current in-game screen.
See the [Discord Presence](#discord-presence) section further below for setup instructions.

## OCR title suggestion in the OCR Reporter
The `ocr_reporter.exe` tool can now use OCR to read the title directly from an unidentified result screenshot and suggest a match automatically, rather than requiring you to identify the song manually.

## Automatic play log entry on recognition
When an unrecognised song is identified through OCR, the result is automatically added to the play log when colorized

## OBS recording and streaming detection
SDVX Helper - Puni Edition can detect when OBS starts or stops a recording or stream.
This allows it to automatically format and export an end-of-session playlist when you finish, so your session log on the Discord webhook will include the timestamps of when the logs were played for easy youtube labeling.

## OCR-based Volforce detection
In addition to image-based detection, it can now use OCR to read your Volforce number directly from the screen, providing a more reliable fallback when the image matcher is uncertain.

<!-- TODO: add Puni Edition feature screenshots here -->

# How to install
Download the ```sdvx_helper_puni_standalone.zip``` at the top of [the release page](https://github.com/Throdax/sdvx_helper/releases)
and extract it to a folder of your choice (excluding the desktop). Click on ```sdvx_helper.exe``` to run it.

An automatic update function is equipped in this tool,
which executes an update process if there are any
updates.

# How to Migrate
If you were previously using an older installation of this helper, you can migrate your existing data — play history, song database, and settings — to the Puni Edition automatically using the bundled migrator tool.
Your result screenshots, recorded songs, and scores are unaffected by the migration.

## What you need
- Your existing helper installation folder (the one containing `sdvx_helper.exe` and your data files).
- The `sdvx_helper_puni_migrator.zip` package downloaded from [the release page](https://github.com/Throdax/sdvx_helper/releases).
- Python installed and available on your PATH (required to convert data files; download from [python.org](https://www.python.org/downloads/) if needed).

## Steps

### 1. Place the migrator files
Extract the contents of `sdvx_helper_puni_migrator.zip` directly into your **existing installation folder** — the same folder that contains your current `sdvx_helper.exe`.
After extracting you should see `migrate.exe` alongside your existing files.

### 2. Run migrate.exe
Double-click `migrate.exe`. The migrator window will open and display a description of what it is about to do.

### 3. Click Migrate
Press the **Migrate** button to start. The migrator will:

1. Back up your entire existing installation to a folder named `<your folder>_old/` in the same parent directory.
2. Extract the new Puni Edition files.
3. Convert your data files to the new format — your play history and song database are preserved.
4. Copy your existing settings across so the application launches with your previous configuration.

Progress is shown in the log area at the bottom of the window.

### 4. Done
Once the log shows that migration has completed successfully, you can close `migrate.exe` and launch `sdvx_helper.exe` as normal.
The `migrate.exe`, `app/` folder, and `runtime/` folder can be deleted at any point after closing the migrator — they are no longer needed.

## Reverting
If you need to go back to your previous installation, reopen `migrate.exe`.
It will detect that a migration has already been performed and show a **Revert Migration** button instead.
Pressing it will restore your old installation from the `<folder>_old/` backup and remove all Puni Edition files.
Note that `migrate.exe`, `app/`, and `runtime/` must be deleted manually after closing the migrator, as they cannot be removed while the migrator itself is running.

# How to setup SDVX Helper - Puni Edition
## 1. Configure WebSocket on OBS(version 28 or later).
If OBSwebsocket is not installed, please download the latest alpha version without 'alpha'
in the name (e.g., ~Windows-Installer.exe)
from [here](https://github.com/obsproject/obs-websocket/releases) and install it.
In OBS, go to 'Tools' in the menu bar,
then select 'WebSocket Server Settings' and configure it as follows."
![image](https://github.com/user-attachments/assets/b8e32ee0-80bc-44ba-ac52-01ad15e4eea3)

## 2. Run sdvx_helper.exe and open '設定(settings)' from the menu bar.
## 3. Enter the port number and password you configured in step 1.
If you see a message saying 'Cannot connect to OBS', suspect the following.  
![image](https://github.com/user-attachments/assets/6daa4288-0698-4101-965d-70bafc7ee082)


## 4. Select the orientation of the screen specified in the e-AMUSEMENT Cloud version of Sound Voltex in the settings.
The settings in Sound Voltex and this tool correspond as follows:
![image](https://github.com/user-attachments/assets/e54e1475-76cf-4887-b496-685d8255fdd1)

## 5. Close the settings window.
## 6. Open 'OBS制御設定(OBS control settings)' from the menu bar.
## 7. Choose the scene name for OBS streaming, select the source name to capture the game screen, and then press 'set' next to the game screen.
These settings are essential to capture the game screen.
![image](https://github.com/user-attachments/assets/7fd08f54-9e0c-4106-9348-b303eb3f4454)

By the way, in the OBS control settings, you can control the visibility of sources for each scene (song selection, playing, results).

For example, you can easily do the following:
- Display hand camera only during play.
- Transition to a different scene on the results/song selection screen.

## 8. "Drag and drop 'out\nowplaying.html' into OBS.
Double-click on 'nowplaying.html' source and set the width to 820
and height to 900 for a nice layout with good margins.
Also, you can set it to show only during gameplay using OBS control settings.
![image](https://github.com/user-attachments/assets/fb76bcf9-4dcc-4d87-aeec-98309d81a673)

Note that the source name must be 'nowplaying.html' or 'nowplaying'
for automatic reloading to occur.
If you directly place images from the 'out' folder in OBS,
they seem to be reloaded automatically.

## (For those who need it) Drag and drop 'out\summary_small.png' into OBS
I've designed it to display a history of 30 songs,
but if you want to reduce the number of songs,
you can trim by Alt+Mouse drag.
The 'out\summary_full.png' is a slightly larger version, including score rates.

To use this feature, be mindful of the following two points:

1. Set the 'リザルト自動保存先フォルダ(The folder where result images are automatically saved)' in the settings window.
2. Results need to be saved after launching.

Regarding 2., it's recommended to enable '更新に関係なく常時保存する(automatically save result images regardless of whether there are updates or not)'.
There's also a feature during summary image generation that filters out only Rank D results (I might expand this further in the future).

Main Specifications:

- Summary image is generated based on result images in the specified result folder(リザルト自動保存先フォルダ) which you selected in the settings window.
- The 'summary_*.png' is updated when results are saved.
- This application aggregates results saved **2 hours before or after the app was launched**.
- The update process is executed once at app startup (if a result was generated within 2 hours).

Due to the specifications, if it doesn't work as expected, consider restarting the app once. 
With a 2-hour window for fetching results, you should see the same image even after a restart.
Also, if a weird image (duplicated, faded colors, etc.)
is included due to a failed result fetch,
deleting the respective result image file will fix it in the next generation process."

## (For those who need it) Turn on notifications when BLASTER GAUGE is at maximum.
By checking 'Remind with sound when BLASTER GAUGE is at maximum' in the settings,
an alert sound (resources\blastermax.wav) will play when the gauge is
at maximum on the song selection screen.

![image](https://github.com/user-attachments/assets/2e29c353-fb45-49d0-b066-b35d00adb7c5)

Also, if you create a text source (GDI+) named 'sdvx_helper_blastermax' in OBS,
it will display the text 'BLASTER GAUGE is at maximum!'
only when the gauge is at maximum.
(To make it easier to use during filter scroll settings,
we have added several dozen full-width spaces at the end.)
Nothing will be displayed if the gauge is not enough.
In OBS control settings, it might be a good idea to show
sdvx_helper_blastermax only on the song selection screen."

## (For those who need it) Display the number of songs played.
If you create a text source (GDI+) named 'sdvx_helper_playcount' in OBS,
it will display the number of songs played after launching this application,
like ```plays: 13```.

Procedure:

1. Right-click in the sources list → Add → Text (GDI+).
2. Customize the settings such as color and font to your liking. (Entering some temporary text can help you remember where you placed it.)
3. Right-click on the created source → Rename → Set it to ```sdvx_helper_playcount```

## (For those who need it) Display VF and grade changes before and after the stream.
By dragging and dropping the following image files into OBS, you can display information such as VF:
- vf_cur.png: Current VF
- vf_pre.png: VF at the app's startup
- class_cur.png: Current grade
- class_pre.png: Grade at the app's startup

# Discord Presence
SDVX Helper - Puni Edition can update your Discord Rich Presence automatically while you play,
showing the current song, your Volforce, and the current in-game screen to anyone viewing your profile.

## Setup
1. Open the settings window from the menu bar.
2. Enable **Discord Presence** in the Discord section.
3. Optionally enable **Show song as title** to promote the current song name to the primary line of your Discord status.
4. Optionally enable **Upload jacket for Discord** to show the current song's jacket art in your Discord status.
   Jackets are hosted temporarily via Litterbox and linked directly in your Discord status.
5. Close the settings window. Discord Presence will activate automatically when the game is detected.

![image](https://github.com/user-attachments/assets/885df080-cb76-4147-b7fb-da5394c05447)

## Notes
- Discord must be running on the same PC for Rich Presence to work.
- Jacket uploads are processed on a background thread and do not affect detection timing.
- If you disable jacket uploading mid-session, the Discord image reverts to the default immediately

#### Default activity 
![image](https://github.com/user-attachments/assets/be3e5acc-8748-4cf8-8d52-02fa2f17b0d8)

#### During song selection
![image](https://github.com/user-attachments/assets/9e9070c7-00c3-4385-8e2f-7acdd0afe14a)

#### During playing a song 
![image](https://github.com/user-attachments/assets/82393612-7842-4910-8260-5651b8bde7cd) ![image](https://github.com/user-attachments/assets/08d2b965-b854-43aa-b74e-4d08454a213a)

#### During the results screen 
![image](https://github.com/user-attachments/assets/c8673807-ad64-4cd5-844e-434537ccfc34)

#### Discord members list 
![image](https://github.com/user-attachments/assets/57f40a36-ad33-4c57-9591-c80f79ec4948)

### Use song name as tittle
With this option enabled, the song title will show up on the Discord members list instead of "Sound Voltext Exeed Gear". The detail activity  is unchanged

![image](https://github.com/user-attachments/assets/e95c23c9-1db7-469c-ae75-46143b5616d1)


# How to Use
If you have set up the above, simply keep it running when doing OBS streaming or recording.
Press the F6 key to save the captured image in the specified folder with the correct orientation.

# Miscellaneous
The license complies with Apache 2.0.

While there is no specific requirement for giving credit,
I would appreciate if you could mention it in the description.

Troubleshooting information will be compiled on [this page](https://github.com/dj-kata/sdvx_helper/wiki/%E3%83%88%E3%83%A9%E3%83%96%E3%83%AB%E3%82%B7%E3%83%A5%E3%83%BC%E3%83%86%E3%82%A3%E3%83%B3%E3%82%B0).

For bug reports or requests, please contact [the Issues section of this repository](https://github.com/Throdax/sdvx_helper/issues)
