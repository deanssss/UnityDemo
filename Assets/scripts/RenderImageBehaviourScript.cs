using System;
using UnityEngine;
using UnityEngine.UI;

public class RenderImageBehaviourScript : MonoBehaviour
{
    AndroidJavaClass jc;
    AndroidJavaObject activity;
    AndroidJavaObject trob;
    AndroidJavaClass trcz;

    RenderTexture cameraView;

    public Camera myCamera;
    public RawImage myImage;

    // Start is called before the first frame update
    void Start()
    {
        try
        {
            cameraView = myCamera.targetTexture;
            jc = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
            activity = jc.GetStatic<AndroidJavaObject>("currentActivity");
        }
        catch (Exception e)
        {
            Debug.Log("RenderImageBehaviourScript Start Exception caught: {0}" + e.Message);
        }
    }

    // Update is called once per frame
    void Update()
    {
        updateFrame("");
    }

    private bool isInist = false;

    public void updateFrame(String param)
    {
        int texId = cameraView.GetNativeTexturePtr().ToInt32();
        Debug.Log("updateFrame camera textureId:" + texId);
        try
        {
            if (cameraView == null)
            {
                cameraView = myCamera.targetTexture;
                Debug.Log("RenderImageBehaviourScript Update cameraView" + cameraView);
            }
            if (!isInist && texId != 0)
            {
                activity.Call("initUnitySurfaceView", cameraView.GetNativeTexturePtr().ToInt32(), 200, 150);
                isInist = true;
            }
        }
        catch (Exception e)
        {
            Debug.Log("RenderImageBehaviourScript Update Exception caught: " + e.Message);
        }
    }
}
