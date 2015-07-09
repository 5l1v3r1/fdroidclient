package org.fdroid.fdroid.views.swap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.fdroid.fdroid.ProgressListener;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.UpdateService;
import org.fdroid.fdroid.data.NewRepoConfig;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.data.RepoProvider;
import org.fdroid.fdroid.localrepo.SwapService;

public class ConnectSwapActivity extends ActionBarActivity implements ProgressListener {

    private static final String TAG = "ConnectSwapActivity";

    /**
     * When connecting to a swap, we then go and initiate a connection with that
     * device and ask if it would like to swap with us. Upon receiving that request
     * and agreeing, we don't then want to be asked whether we want to swap back.
     * This flag protects against two devices continually going back and forth
     * among each other offering swaps.
     */
    public static final String EXTRA_PREVENT_FURTHER_SWAP_REQUESTS = "preventFurtherSwap";

    @Nullable
    private Repo repo;

    private NewRepoConfig newRepoConfig;
    private TextView descriptionTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.swap_confirm_receive);

        descriptionTextView = (TextView) findViewById(R.id.text_description);

        findViewById(R.id.no_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
        findViewById(R.id.yes_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only confirm the action, and then return a result...
        newRepoConfig = new NewRepoConfig(this, getIntent());
        if (newRepoConfig.isValidRepo()) {
            descriptionTextView.setText(getString(R.string.swap_confirm_connect, newRepoConfig.getHost()));
        } else {
            // TODO: Show error message on screen (not in popup).
            // TODO: I don't think we want to continue with this at all if the repo config is invalid,
            // how should we boot the user from this screen in this case?
        }
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void onProgress(Event event) {
        // TODO: Show progress, but we can worry about that later.
        // Might be nice to have it nicely embedded in the UI, rather than as
        // an additional dialog. E.g. White text on blue, letting the user
        // know what we are up to.

        switch (event.type) {
            case UpdateService.EVENT_COMPLETE_AND_SAME:
                Log.i(TAG, "EVENT_COMPLETE_AND_SAME");
            case UpdateService.EVENT_COMPLETE_WITH_CHANGES:
                Log.i(TAG, "EVENT_COMPLETE_WITH_CHANGES");
                Intent intent = new Intent(this, SwapAppListActivity.class);
                intent.putExtra(SwapAppListActivity.EXTRA_REPO_ID, repo.getId());
                startActivity(intent);
                finish();
            /*
            // TODO: Load repo from database to get proper name. This is what the category we want to select will be called.
            intent.putExtra("category", newRepoConfig.getHost());
            getActivity().setResult(Activity.RESULT_OK, intent);
            */
                break;
            case UpdateService.EVENT_ERROR:
                // TODO: Show message on this screen (with a big "okay" button that goes back to F-Droid activity)
                // rather than finishing directly.
                finish();
                break;
        }
    }

    private void confirm() {
        repo = ensureRepoExists();
        if (repo != null) {
            UpdateService.updateRepoNow(repo.address, this).setListener(this);
        }
    }

    private Repo ensureRepoExists() {
        if (!newRepoConfig.isValidRepo()) {
            return null;
        }

        // TODO: newRepoConfig.getParsedUri() will include a fingerprint, which may not match with
        // the repos address in the database. Not sure on best behaviour in this situation.
        Repo repo = RepoProvider.Helper.findByAddress(this, newRepoConfig.getRepoUriString());
        if (repo == null) {
            ContentValues values = new ContentValues(6);

            // TODO: i18n and think about most appropriate name. Although it wont be visible in
            // the "Manage repos" UI after being marked as a swap repo here...
            values.put(RepoProvider.DataColumns.NAME, getString(R.string.swap_repo_name));
            values.put(RepoProvider.DataColumns.ADDRESS, newRepoConfig.getRepoUriString());
            values.put(RepoProvider.DataColumns.DESCRIPTION, ""); // TODO;
            values.put(RepoProvider.DataColumns.FINGERPRINT, newRepoConfig.getFingerprint());
            values.put(RepoProvider.DataColumns.IN_USE, true);
            values.put(RepoProvider.DataColumns.IS_SWAP, true);
            Uri uri = RepoProvider.Helper.insert(this, values);
            repo = RepoProvider.Helper.findByUri(this, uri);
        }

        attemptSwapBack();

        return repo;
    }

    /**
     * Only ask server to swap with us, if we are actually running a local repo service.
     * It is possible to have a swap initiated without first starting a swap, in which
     * case swapping back is pointless.
     */
    private void attemptSwapBack() {

        if (!newRepoConfig.isValidRepo() || newRepoConfig.preventFurtherSwaps()) {
            return;
        }

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                SwapService service = ((SwapService.Binder) binder).getService();
                if (service.isEnabled()) {
                    service.askServerToSwapWithUs(newRepoConfig);
                }
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        Intent intent = new Intent(this, SwapService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }
}
