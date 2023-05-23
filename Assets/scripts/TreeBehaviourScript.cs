using System.Collections;
using System.Collections.Generic;
using System;
using UnityEngine;
using UnityEngine.UI;
using TMPro;

public class TreeBehaviourScript : MonoBehaviour
{
    AndroidJavaClass jc;
    AndroidJavaObject activity;
    public TextMeshProUGUI text1;

    // Start is called before the first frame update
    void Start()
    {
        try {
            jc = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
            activity = jc.GetStatic<AndroidJavaObject>("currentActivity");
        } catch (Exception e) {
            Debug.Log("Exception caught: {0}" + e.Message);
        }
    }

    // Update is called once per frame
    void Update()
    {
        if (Input.touchCount == 1 && Input.touches[0].phase == TouchPhase.Began)
        {
            try {
                activity.Call("showToast", "Message from Unity.");
            } catch (Exception e) {
                Debug.Log("Exception caught:" + e.Message);
            }
        }
        if (Input.GetKeyUp(KeyCode.Escape))
        {
            try {
                activity.Call("onBackPressed");
            } catch (Exception e) {
                Debug.Log("Exception caught:" + e.Message);
            }
        }
    }

    public void setText(String msg)
    {
        text1.text = msg;
    }
}
