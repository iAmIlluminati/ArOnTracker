/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gru.augmentedimage.rendering;

import android.content.Context;
import android.util.Log;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Pose;
import com.gru.common.rendering.ObjectRenderer;

import java.io.IOException;
import java.util.*;

/** Renders an augmented image. */
public class AugmentedImageRenderer {
//  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();

  //Stores the scale and position for the various objects
  Map<String, Float[]> para = new HashMap<String, Float[]>();

  private static final String TAG = "AugmentedImageRenderer";
//  private final ObjectRenderer ArObj = new ObjectRenderer();
  private final ArrayList<ObjectRenderer> TempObjs = new ArrayList<ObjectRenderer>();

  private static final float TINT_INTENSITY = 0.1f;
  private static final float TINT_ALPHA = 1.0f;
  private static final int[] TINT_COLORS_HEX = {
    0x000000, 0xF44336, 0xE91E63, 0x9C27B0, 0x673AB7, 0x3F51B5, 0x2196F3, 0x03A9F4, 0x00BCD4,
    0x009688, 0x4CAF50, 0x8BC34A, 0xCDDC39, 0xFFEB3B, 0xFFC107, 0xFF9800,
  };


  public AugmentedImageRenderer() {
    //    ArObjScaleFactor = ArObjScaleFactor*40f;  //FuelInj.obj file
//    ArObjScaleFactor = ArObjScaleFactor*10f;  //IcEngine.obj file
//    ArObjScaleFactor = ArObjScaleFactor*90f;  //v8Engine.obj file
//    ArObjScaleFactor = ArObjScaleFactor*30f;  //Engine.obj file
//    ArObjScaleFactor = ArObjScaleFactor*2f;  //Piston.obj file
//    Pose ArObjModelLocalOffset = Pose.makeTranslation(-0.2f,0.13531927f,-0.1f); //different pose for piston.obj

    para.put("IcEngine", new Float[] {0f,0.13531927f,0f,10f});
    para.put("Engine", new Float[] {0f,0.13531927f,0f,30f});
    para.put("FuelInj", new Float[] {0f,0.13531927f,0f,40f});
    para.put("Piston", new Float[] {-0.2f,0.13531927f,-0.1f,2f});
    para.put("v8Engine", new Float[] {0f,0.13531927f,0f,90f});
  }

  public void createOnGlThread(Context context,String img ) throws IOException {
    Log.i(TAG,img);
//    ArObj.createOnGlThread(context, "models/Engine2.obj");
    //TODO The texture is not added. The paramter is passed for namesake.
    String imgs[] = { "IcEngine.obj","FuelInj.obj","v8Engine.obj","Engine.obj","Piston.obj" };
    for(String i :imgs)
    {
      ObjectRenderer ArObj = new ObjectRenderer();
      ArObj.createOnGlThread(context, "models/"+i, "models/steel.png");
      ArObj.setMaterialProperties(0.0f, 1.5f, 0.5f, 2.0f);
      TempObjs.add(ArObj);
    }
  }
  // Adjust size of detected image and render it on-screen
  public void draw(
          float[] viewMatrix,
          float[] projectionMatrix,
          AugmentedImage augmentedImage,
          Anchor centerAnchor,
          float[] colorCorrectionRgba,
          String key,
          int index) {

      System.out.println(key);
      Float trans[] = para.get(key);


    float[] tintColor = convertHexToColor(TINT_COLORS_HEX[augmentedImage.getIndex() % TINT_COLORS_HEX.length]);
    final float ArObjEdgeSize = 492.65f; // Magic number of ArObj size
    final float maxImageEdgeSize = Math.max(augmentedImage.getExtentX(), augmentedImage.getExtentZ()); // Get largest detected image edge size

    Pose anchorPose = centerAnchor.getPose();

    float ArObjScaleFactor =  maxImageEdgeSize / ArObjEdgeSize; // scale to set ArObj to image size
    ArObjScaleFactor = ArObjScaleFactor*trans[3];  //FuelInj.obj file

    //    messageSnackbarHelper.showError(this, String.valueOf(ArObjScaleFactor));

    //TODO Scale the Obj dynamically to fit the screen


//    System.out.println("Scale factor :"+ArObjScaleFactor);
    float[] modelMatrix = new float[16];

    // OpenGL Matrix operation is in the order: Scale, rotation and Translation
    // So the manual adjustment is after scale
    // The 251.3f and 129.0f is magic number from the ArObj obj file
    // You mustWe need to do this adjustment because the ArObj obj file
    // is not centered around origin. Normally when you
    // work with your own model, you don't have this problem.
//    System.out.println("Value : "+30.0f * ArObjScaleFactor);
    //KComment change the translation
    Pose ArObjModelLocalOffset = Pose.makeTranslation(trans[0],trans[1],trans[2]); //default translation
//    Pose ArObjModelLocalOffset = Pose.makeTranslation(
//            -251.3f * ArObjScaleFactor,
//            0.0f,
//            129.0f * ArObjScaleFactor);
    for(int i=0;i<5;i++){
      if(i!=index){
        Pose LocalOffset = Pose.makeTranslation(10000,0,0); //default translation
        anchorPose.compose(LocalOffset).toMatrix(modelMatrix, 0);

        TempObjs.get(i).updateModelMatrix(modelMatrix, 0, 0,0); // This line relies on a change in ObjectRenderer.updateModelMatrix later in this codelab.
        TempObjs.get(i).draw(viewMatrix, projectionMatrix, colorCorrectionRgba, tintColor);
      }
    }
    anchorPose.compose(ArObjModelLocalOffset).toMatrix(modelMatrix, 0);
    TempObjs.get(index).updateModelMatrix(modelMatrix, ArObjScaleFactor, ArObjScaleFactor, ArObjScaleFactor); // This line relies on a change in ObjectRenderer.updateModelMatrix later in this codelab.
    TempObjs.get(index).draw(viewMatrix, projectionMatrix, colorCorrectionRgba, tintColor);
  }

  private static float[] convertHexToColor(int colorHex) {
    // colorHex is in 0xRRGGBB format

    float red = ((colorHex & 0xFF0000) >> 16) / 255.0f * TINT_INTENSITY;
    float green = ((colorHex & 0x00FF00) >> 8) / 255.0f * TINT_INTENSITY;
    float blue = (colorHex & 0x0000FF) / 255.0f * TINT_INTENSITY;
    return new float[] {red, green, blue, TINT_ALPHA};
  }
}
