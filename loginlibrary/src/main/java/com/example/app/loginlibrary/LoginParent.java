package com.example.app.loginlibrary;

import android.app.Activity;
import android.content.Intent;

public class LoginParent extends Activity {

    protected int FinishAll = 0x01 ;//登录模块退出关闭
    protected Class LoginToRegister = RegisterActivity.class ;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( resultCode == FinishAll ){
            setResult(FinishAll,data);
            finish();
        }
    }
}
