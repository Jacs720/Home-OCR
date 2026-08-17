package com.baidu.paddle.lite.demo.ocr;

interface ISwipeUserService {
    boolean swipe(int screenWidth, int screenHeight);
    boolean scrollDown(int screenWidth, int screenHeight);
    boolean scrollUp(int screenWidth, int screenHeight);
}
