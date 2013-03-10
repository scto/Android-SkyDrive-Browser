package com.killerud.skydrive;

import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.commonsware.cwac.tlv.TouchListView;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioPlayQueueActivity extends SherlockActivity
{
    private TouchListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_queue_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        listView = (TouchListView) findViewById(R.id.audioQueueList);
        listView.setDropListener(onDrop);
        listView.setRemoveListener(onRemove);

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        Toast.makeText(getApplicationContext(), R.string.audioQueueInstructions, Toast.LENGTH_SHORT).show();
        registerBroadcastReceiver();
        startService(new Intent(this, AudioPlaybackService.class));
        bindService(new Intent(this, AudioPlaybackService.class),
                audioPlaybackServiceConnection, Context.BIND_ABOVE_CLIENT);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
        unbindService(audioPlaybackServiceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.audio_queue_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                return true;
            case R.id.audioQueueClear:
                clearNowPlayingQueue();
                return true;
            default:
                return false;
        }
    }

    private void clearNowPlayingQueue()
    {
        ((AudioQueueAdapter) listView.getAdapter()).getQueue().clear();
        audioPlaybackService.populateNowPlayingWithUpdatedQueue(((AudioQueueAdapter) listView.getAdapter()).getQueue());
        ((AudioQueueAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    private TouchListView.DropListener onDrop = new TouchListView.DropListener()
    {
        @Override
        public void drop(int from, int to)
        {
            SkyDriveAudio item = (SkyDriveAudio) listView.getAdapter().getItem(from);
            ((AudioQueueAdapter) listView.getAdapter()).remove(from);
            ((AudioQueueAdapter) listView.getAdapter()).insertAndDisplaceExisting(item, to);
            ((AudioQueueAdapter) listView.getAdapter()).notifyDataSetChanged();
            updateNowPlayingQueue();
        }
    };

    private TouchListView.RemoveListener onRemove = new TouchListView.RemoveListener()
    {
        @Override
        public void remove(int which)
        {
            ((AudioQueueAdapter) listView.getAdapter()).remove(which);
            ((AudioQueueAdapter) listView.getAdapter()).notifyDataSetChanged();
            updateNowPlayingQueue();
        }
    };

    private void updateNowPlayingQueue()
    {
        audioPlaybackService.populateNowPlayingWithUpdatedQueue(((AudioQueueAdapter) listView.getAdapter()).getQueue());
    }


    private AudioPlaybackService audioPlaybackService;
    private ServiceConnection audioPlaybackServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            audioPlaybackService = ((AudioPlaybackService.AudioPlaybackServiceBinder) iBinder).getService();
            SkyDriveAudio[] container = new SkyDriveAudio[audioPlaybackService.NOW_PLAYING.size()];
            listView.setAdapter(
                    new AudioQueueAdapter(
                            new ArrayList<SkyDriveAudio>(
                                    Arrays.asList(
                                            audioPlaybackService.NOW_PLAYING.toArray(container)
                                    )
                            )
                    ));
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            audioPlaybackService = null;
        }
    };

    private void registerBroadcastReceiver()
    {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Constants.ACTION_SONG_CHANGE);
        registerReceiver(broadcastReceiver, new IntentFilter(ifilter));
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equals(Constants.ACTION_SONG_CHANGE))
            {
                ((AudioQueueAdapter) listView.getAdapter()).updateDataSetOnSongPlayed();
            }
        }
    };

    class AudioQueueAdapter extends BaseAdapter
    {
        private ArrayList<SkyDriveAudio> queue;

        AudioQueueAdapter(ArrayList<SkyDriveAudio> queue)
        {
            this.queue = queue;
        }

        public List<SkyDriveAudio> getQueue()
        {
            return this.queue;
        }

        public void insertAndDisplaceExisting(SkyDriveAudio item, int position)
        {
            ArrayList<SkyDriveAudio> temp = new ArrayList<SkyDriveAudio>();

            for (int i = 0; i < position; i++)
            {
                temp.add(this.queue.get(i));
            }

            temp.add(item);

            for (int i = position; i < this.queue.size(); i++)
            {
                temp.add(this.queue.get(i));
            }

            this.queue = (ArrayList<SkyDriveAudio>) temp.clone();
            temp.clear();
        }

        public void updateDataSetOnSongPlayed()
        {
            SkyDriveAudio[] container = new SkyDriveAudio[audioPlaybackService.NOW_PLAYING.size()];
            this.queue = new ArrayList<SkyDriveAudio>(
                    Arrays.asList(
                            audioPlaybackService.NOW_PLAYING.toArray(container)
                    )
            );
            notifyDataSetChanged();
        }


        public void remove(int position)
        {
            this.queue.remove(position);
        }

        @Override
        public int getCount()
        {
            return this.queue.size();
        }

        @Override
        public Object getItem(int position)
        {
            return queue.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return 0;
        }

        @Override
        public View getView(int position, View convertView,
                            ViewGroup parent)
        {
            View row = convertView;

            if (row == null)
            {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.music_playlist_row, parent, false);
            }

            TextView label = (TextView) row.findViewById(R.id.label);
            label.setText(this.getItem(position).toString());

            return (row);
        }
    }
}
