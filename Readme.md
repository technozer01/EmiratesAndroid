# Accura Emirates

#### Step 1: Add files to project assets folder:
```
Create assets folder under app/src/main and add licence file in to assets folder.
1. key.licence // for Accura Emirates
2. accuraface.license // for Accura Face Match
Generate your Accura licence from https://accurascan.com/developer/dashboard
```
#### Step 2: Ocr camera details

1. Getting Ocr camera code details from `app\src\main\java\com\docrecog\scan\CameraActivity.java`.
2. Make sure initialized `RecogEngine.initEngine()`.
3. Update filter in `setFilter()` method on `line no. 251` in `app\src\main\java\com\docrecog\scan\RecogEngine.java` as per requirement.
4. Check `void showResultActivity()` method on `line no.492` to open result view after scanned complete.

#### Step 3: Display result and FaceMatch

1. Check `OcrResultActivity.java` to display ocr data and perform FaceMatch
2. Check `setDocumentData()` to display Ocr Result.

Facematch code.

1. Make sure initilaized Face Match `initEngine()` on `line no.520`
2. Make sure implement `com.inet.facelock.callback.FaceCallback` to your activity context for Face match.
3. Document "Face" used as "Left face" `FaceLockHelper.DetectLeftFace();`
4. Selfi camera "Face" used as "Right Face" `FaceLockHelper.DetectRightFace();`
5. `FaceLockHelper.Similarity();` to get Match score of doument face and selfi cmaera Face

Make sure follow below method as it is like `OcrResultActivity.java`
1. Line no. 520 `initEngine()`
2. Line no. 587 `onLeftDetect(FaceDetectionResult faceResult)`
3. Line no. 629 `onRightDetect(FaceDetectionResult faceResult)`
4. Line no. 650 `calcMatch()`
5. Follow Line no. 339 `onActivityResult(int requestCode, int resultCode, Intent data)` after taking selfi from camera.
   

