using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class CameraRotateBehaviourScript : MonoBehaviour
{
    public float speed = 500;

    // Start is called before the first frame update
    void Start()
    {
    }

    // Update is called once per frame
    void Update()
    {
        cameraRotate();
    }

    private void cameraRotate()
    {
        transform.RotateAround(new Vector3(0, 0, 0), Vector3.up, speed * Time.deltaTime);
    }
}
