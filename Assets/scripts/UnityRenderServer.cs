using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using UnityEngine;

public class UnityRenderServer : MonoBehaviour
{

    public Camera cam;
    public int port = 10002;

    RenderTexture cameraView = null;

    Socket socket = null;

    Thread thread = null;

    bool success = true;

    Dictionary<string, Client> clients = new Dictionary<string, Client>();

    Vector3 old_position;   // 旧位置
    Quaternion old_rotation;    // 旧旋转

    void Start()
    {
        cameraView = new RenderTexture(Screen.width, Screen.height, 24);
        cameraView.enableRandomWrite = true;

        cam.targetTexture = cameraView;
        old_position = transform.position;
        old_rotation = transform.rotation;

        // 开启Socket
        socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
        socket.Bind(new IPEndPoint(IPAddress.Parse("127.0.0.1"), port));
        socket.Listen(100);

        // 开启一个线程发送渲染数据
        thread = new Thread(new ThreadStart(OnStart));
        thread.Start();
    }

    int isNewAdd = 0;

    void OnStart()
    {
        Debug.Log("Socket创建成功");
        while (thread.ThreadState == ThreadState.Running)
        {
            Socket _socket = socket.Accept();
            if (clients.ContainsKey(_socket.RemoteEndPoint.ToString()))
            {
                try
                {
                    clients[_socket.RemoteEndPoint.ToString()].socket.Shutdown(SocketShutdown.Both);
                }
                catch
                {
                }
                clients.Remove(_socket.RemoteEndPoint.ToString());
            }

            Client client = new Client
            {
                socket = _socket
            };

            clients.Add(_socket.RemoteEndPoint.ToString(), client);

            isNewAdd = 1;
        }
    }

    void Update()
    {
        if (success && clients.Count > 0)
        {
            success = false;
            SendTexture();
        }

        if (isNewAdd > 0)
        {
            isNewAdd = 0;
            SendTexture(1);
        }
    }

    void OnGUI()
    {
        GUI.DrawTexture(new Rect(10, 10, 240, 135), cameraView, ScaleMode.StretchToFill);
    }

    void OnApplicationQuit()
    {
        try
        {
            socket.Shutdown(SocketShutdown.Both);
        }
        catch { }

        try
        {
            thread.Abort();
        }
        catch { }
    }

    Texture2D screenShot = null;
    int gc_count = 0;

    void SendTexture(int isInt = 0)
    {
        if ((!old_position.Equals(transform.position) || !old_rotation.Equals(transform.rotation)) || isInt == 1)
        {
            if (null == screenShot)
            {
                screenShot = new Texture2D(Screen.width, Screen.height, TextureFormat.RGB24, false);
            }

            // 读取屏幕像素进行渲染
            RenderTexture.active = cameraView;
            screenShot.ReadPixels(new Rect(0, 0, cameraView.width, cameraView.height), 0, 0);
            RenderTexture.active = null;
            byte[] bytes = screenShot.EncodeToJPG(100);

            foreach (var val in clients.Values)
            {
                try
                {
                    val.socket.Send(bytes);
                }
                catch
                {
                    if (!val.socket.Connected)
                    {
                        clients.Remove(val.socket.RemoteEndPoint.ToString());
                    }
                }
            }
            gc_count++;
            if (gc_count > 5000)
            {
                gc_count = 0;
                GC.Collect(2);
            }
            Debug.Log("发送数据:" + (float)bytes.Length / 1024f + "KB");

            old_position = cam.transform.position;
            old_rotation = cam.transform.rotation;
        }
        success = true;
    }

    void OnDestroy()
    {
        try
        {
            socket.Shutdown(SocketShutdown.Both);
        }
        catch { }

        try
        {
            thread.Abort();
        }
        catch { }
    }
}

class Client
{
    public Socket socket = null;
}