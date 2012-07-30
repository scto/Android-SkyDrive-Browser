Changelog
=========

**2.5**
- Updated automatic camera upload for broader device support. Scrapped FileObserver for ContentObserver.
- Updated settings screen
- Storage quota available in settings
- Music controls acces in menu

**2.4.1**
- Hotfix for the Microsoft account rollout bug

**2.4**
- File preview added for Office- and Open Documents (text documents, spreadsheets, and presentations)
- Thumbnail preview for SkyDrive videos
- All new music player
	+ Plays in background, letting you use the app while playing
	+ Queue up songs to be played in order
	+ Shuffle the playlist or make it repeat
- Project released under the Apache 2.0 license
	
**2.3**
- Files shared with you can now be reached via a menu option, and selected files can be shared through any app that can send text content (like GMail, Twitter, Facebook)
- New SkyDrive permission required for the above. This does not affect your phone.
- Confirmation dialog on exit can be enabled in Settings
- Uploads where the file exists already now updates the file stored on SkyDrive. Download, edit and upload finally works!
- Folders can now be downloaded
- Removed initial progress dialog, replaced with spinner in actionbar
- Improved the local file browsers. Thumbs, proper handling of selections and orientation changes. 

**2.2**
- Settings to limit data to WiFi appwide and for auto uploading of camera images
- Rebranding to comply with SkyDrive branding guidelines
- App should no longer crash during sign in when changing orientation
- Fixed uploader crash
- Fixed audio dialog crash

**2.1.1**
- Service crash hotfix

**2.1**
- Automatic camera image uploads option
- Selected files and current folder properly saved during orientation change for all browsers
- Icons updated and are now color and style consistent across Android versions
- Video streaming window with media controls (file type support varies from phone to phone)
- Fixed home caret issue
- Rename dialog now has the original file name initially
- Download and upload notifications dissapear when clicked
- The issue with users experiencing crashes on Sign out should be resolved
- Crashes on file paste should now be fixed

**2.0**
- ActionBarSherlock! Action bar goodness for Android 2.
- Checkboxes are gone. In accordance with design guidelines longclick selects files.
- Contextual Action bar!
- Clickable home icon for navigation
- Confirmation dialog on delete
- Upload multiple files at once using share!
- Thumbnails and icons now have a static size
- Thumbnails now load from a local cache after first download
- Thumbnail download and display now work for all supported versions, including ICS
- File browser view for checking and deleting saved files
- Uploading now has its own activity
- Image scaling improved, JPGs are now shown
- Select/deselect all in SkyDrive browser
- SkyDrive permissions restricted
	+ Access information at all times is used for auto-login after the user has signed in once
	+ SkyDrive is used for obvious reasons
- You can now upload files from the whole phone, not just the SD card
- Browser is organized alphabetically with folders first
- File icons added for many file types
- Sign-out button added
- Many, many bug fixes


**1.5.2**
- Image thumbnails (actual fix from 1.5.1. Thumbnail loading still a bit slow.)
- New icon by Tom Emery

**1.5**
- Copy/Cut and paste file(s)
- Delete file(s)
- Rename file(s)
- Batch download
- Batch upload
- Clicking notification on download opens the file (if supported app for reading is installed)
- No more progress dialog spam. Notifications and toolbar do the job instead.
- Photo dialog scales images
- Updated icons