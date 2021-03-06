package com.ferg.awfulapp;

import java.io.File;
import java.util.LinkedList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Window;
import com.androidquery.AQuery;
import com.ferg.awfulapp.constants.Constants;
import com.ferg.awfulapp.network.NetworkUtils;
import com.ferg.awfulapp.preferences.AwfulPreferences;
import com.ferg.awfulapp.service.AwfulSyncService;

/**
 * Convenience class to avoid having to call a configurator's lifecycle methods everywhere. This
 * class should avoid implementing things directly; the ActivityConfigurator does that job.
 * 
 * Most Activities in this awful app should extend this guy; that will provide things like locking
 * orientation according to user preference.
 * 
 * This class also provides a few helper methods for grabbing preferences and the like.
 */
public class AwfulActivity extends SherlockFragmentActivity implements ServiceConnection, AwfulUpdateCallback {
    private static final String TAG = "AwfulActivity";
	private ActivityConfigurator mConf;
    private Messenger mService = null;
    private LinkedList<Message> mMessageQueue = new LinkedList<Message>();
    
    private boolean loggedIn = false;
    
    protected AQuery aq;

    private TextView mTitleView;
    
    protected AwfulPreferences mPrefs;
    
    public void reauthenticate(){
    	NetworkUtils.clearLoginCookies(this);
        startActivityForResult(new Intent(this, AwfulLoginActivity.class), Constants.LOGIN_ACTIVITY_REQUEST);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        aq = new AQuery(this);
        mConf = new ActivityConfigurator(this);
        mConf.onCreate();
        mPrefs = new AwfulPreferences(this, this);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        loggedIn = NetworkUtils.restoreLoginCookies(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mConf.onStart();
        bindService(new Intent(this, AwfulSyncService.class), this, BIND_AUTO_CREATE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mConf.onResume();
        
        if (isLoggedIn()) {
            Log.v(TAG, "Cookie Loaded!");
        } else {
        	if(!(this instanceof AwfulLoginActivity)){
        		reauthenticate();
        	}
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        mConf.onPause();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        mConf.onStop();
        unbindService(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mConf.onDestroy();
    }


    @Override
	protected void onActivityResult(int request, int result, Intent intent) {
		super.onActivityResult(request, result, intent);
    	Log.w(TAG,"onActivityResult: " + request+" result: "+result);
		if(request == Constants.LOGIN_ACTIVITY_REQUEST && result == Activity.RESULT_CANCELED){
			finish();
		}
	}

	protected void setActionBar() {
        ActionBar action = getSupportActionBar();
        action.setDisplayShowTitleEnabled(false);
        action.setCustomView(R.layout.actionbar_title);
        mTitleView = (TextView) action.getCustomView();
        mTitleView.setMovementMethod(new ScrollingMovementMethod());
        updateActionbarTheme(mPrefs);
        action.setDisplayShowCustomEnabled(true);
        action.setDisplayHomeAsUpEnabled(true);
    }
    
    protected void updateActionbarTheme(AwfulPreferences aPrefs){
        ActionBar action = getSupportActionBar();
        if(action != null && mTitleView != null){
	        action.setBackgroundDrawable(new ColorDrawable(aPrefs.actionbarColor));
	        mTitleView.setTextColor(aPrefs.actionbarFontColor);
	        setPreferredFont(mTitleView, Typeface.NORMAL);
        }
    }

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "Service Connected!");
        mService = new Messenger(service);
        for(Message msg : mMessageQueue){
        	try {
				mService.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
        }
        mMessageQueue.clear();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
        Log.i(TAG, "Service Disconnected!");
		mService = null;
	}
	public void sendMessage(Messenger callback, int messageType, int id, int arg1){
		sendMessage(callback, messageType, id, arg1, null);
	}
	public void sendMessage(Messenger callback, int messageType, int id, int arg1, Object obj){
		try {
            Message msg = Message.obtain(null, messageType, id, arg1);
            msg.replyTo = callback;
            msg.obj = obj;
    		if(mService != null){
    			mService.send(msg);
    		}else{
    			mMessageQueue.add(msg);
    		}
        } catch (RemoteException e) {
            e.printStackTrace();
        }
	}

    public void displayUserCP() {
    	displayForum(Constants.USERCP_ID, 1);
    }
    
    public void displayThread(int id, int page, int forumId, int forumPage){
    	startActivity(new Intent().setClass(this, ThreadDisplayActivity.class)
    							  .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    							  .putExtra(Constants.THREAD_ID, id)
    							  .putExtra(Constants.THREAD_PAGE, page)
    							  .putExtra(Constants.FORUM_ID, forumId)
    							  .putExtra(Constants.FORUM_PAGE, forumPage));
    }
    
    public void displayForum(int id, int page){
    	startActivity(new Intent().setClass(this, ForumsIndexActivity.class)
    							  .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    							  .putExtra(Constants.FORUM_ID, id)
    							  .putExtra(Constants.FORUM_PAGE, page));
    }
    
    public void displayForumIndex(){
    	startActivity(new Intent().setClass(this, ForumsIndexActivity.class)
				  .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
    
    public void displayQuickBrowser(String url){
        AwfulWebFragment.newInstance(url).show(getSupportFragmentManager().beginTransaction(), "awful_web_dialog");
    }
    
	public void displayReplyWindow(int threadId, int postId, int type) {
    	Bundle args = new Bundle();
        args.putInt(Constants.THREAD_ID, threadId);
        args.putInt(Constants.EDITING, type);
        args.putInt(Constants.POST_ID, postId);
    	startActivityForResult(new Intent(this, PostReplyActivity.class).putExtras(args), PostReplyFragment.REQUEST_POST);
	}
    
    public void setActionbarTitle(String aTitle, Object requestor) {
        ActionBar action = getSupportActionBar();
        if(action != null){
        	mTitleView = (TextView) action.getCustomView();
        }
    	if(aTitle != null && mTitleView != null && aTitle.length()>0){
    		mTitleView.setText(Html.fromHtml(aTitle));
			mTitleView.scrollTo(0, 0);
    	}else{
    		Log.e(TAG, "FAILED setActionbarTitle - "+aTitle);
    	}
    }
    
    public AwfulApplication getAwfulApplication(){
    	return (AwfulApplication) getApplication();
    }
    
    public void setPreferredFont(View view, int flags){
    	if(getApplication() != null && view != null){
    		((AwfulApplication)getApplication()).setPreferredFont(view, flags);
    	}
    }
    
    public void setPreferredFont(View view){
    	setPreferredFont(view, -1);
    }
    
    public static boolean isHoneycomb(){
    	return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

	@Override
	public void onPreferenceChange(AwfulPreferences prefs) {
		updateActionbarTheme(prefs);
	}
	
	protected boolean isLoggedIn(){
		if(!loggedIn){
			loggedIn = NetworkUtils.restoreLoginCookies(this);
		}
		return loggedIn;
	}

	public boolean isFragmentVisible(AwfulFragment awfulFragment) {
		return true;
	}
	
	//UNUSED - I don't know why I put them in the same interface. Oh well.
	@Override
	public void loadingFailed(Message aMsg) {}
	@Override
	public void loadingStarted(Message aMsg) {}
	@Override
	public void loadingSucceeded(Message aMsg) {}
	@Override
	public void loadingUpdate(Message aMsg) {}

	public void fragmentClosing(AwfulFragment fragment) {}

	public void setLoadProgress(int percent) {
		setSupportProgressBarVisibility(percent<100);
    	setSupportProgressBarIndeterminateVisibility(false);
		setSupportProgress(percent*100);
	}
	
	public void hideProgressBar(){
		setSupportProgressBarVisibility(false);
    	setSupportProgressBarIndeterminateVisibility(false);
	}

	/**
	 * AwfulFragments have the capability to broadcast messages to other fragments on the same activity.
	 * Override this method, then pass these Strings to any internal fragments.
	 * @param type
	 * @param contents
	 */
	public void fragmentMessage(String type, String contents) {	}//subclasses should implement this

	


	@Override
	public File getCacheDir() {
		Log.e(TAG,"getCacheDir(): "+super.getCacheDir());
		return super.getCacheDir();
	}
}
