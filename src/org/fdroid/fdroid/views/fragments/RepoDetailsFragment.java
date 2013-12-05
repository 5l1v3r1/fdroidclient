package org.fdroid.fdroid.views.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.fdroid.fdroid.*;

public class RepoDetailsFragment extends Fragment {

    public static final String ARG_REPO_ID = "repo_id";

    /**
     * If the repo has been updated at least once, then we will show
     * all of this info, otherwise it will be hidden.
     */
    private static final int[] SHOW_IF_EXISTS = {
        R.id.label_repo_name,
        R.id.text_repo_name,
        R.id.label_description,
        R.id.text_description,
        R.id.label_num_apps,
        R.id.text_num_apps,
        R.id.label_last_update,
        R.id.text_last_update,
        R.id.label_signature,
        R.id.text_signature,
        R.id.text_signature_description
    };

    /**
     * If the repo has <em>not</em> been updated yet, then we only show
     * these, otherwise they are hidden.
     */
    private static final int[] HIDE_IF_EXISTS = {
        R.id.text_not_yet_updated,
        R.id.btn_update
    };

    private static final int DELETE = 0;

    public void setRepoChangeListener(OnRepoChangeListener listener) {
        repoChangeListener = listener;
    }

    private OnRepoChangeListener repoChangeListener;

    public static interface OnRepoChangeListener {

        /**
         * This fragment is responsible for getting confirmation from the
         * user, so you should presume that the user has already consented
         * and confirmed to the deletion.
         */
        public void onDeleteRepo(DB.Repo repo);

        public void onRepoDetailsChanged(DB.Repo repo);

        public void onEnableRepo(DB.Repo repo);

        public void onDisableRepo(DB.Repo repo);

        public void onUpdatePerformed(DB.Repo repo);

    }

    private ProgressListener updateProgressListener = new ProgressListener() {
        @Override
        public void onProgress(Event event) {
            if (event.type == UpdateService.STATUS_COMPLETE) {
                reloadRepoDetails();
                updateView((ViewGroup)getView());
            }
        }
    };

    // TODO: Currently initialised in onCreateView. Not sure if that is the
    // best way to go about this...
    private DB.Repo repo;

    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    /**
     * After, for example, a repo update, the details will have changed in the
     * database. However, or local reference to the DB.Repo object will not
     * have been updated. The safest way to deal with this is to reload the
     * repo object directly from the database.
     */
    private void reloadRepoDetails() {
        try {
            DB db = DB.getDB();
            repo = db.getRepo(repo.id);
        } finally {
            DB.releaseDB();
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int repoId = getArguments().getInt(ARG_REPO_ID);
        DB db = DB.getDB();
        repo = db.getRepo(repoId);
        DB.releaseDB();

        if (repo == null) {
            Log.e("FDroid", "Error showing details for repo '" + repoId + "'");
            return new LinearLayout(container.getContext());
        }

        ViewGroup repoView = (ViewGroup)inflater.inflate(R.layout.repodetails, null);
        updateView(repoView);

        // Setup listeners here, rather than in updateView(...),
        // because otherwise we will end up adding multiple listeners with
        // subsequent calls to updateView().
        EditText inputUrl = (EditText)repoView.findViewById(R.id.input_repo_url);
        inputUrl.addTextChangedListener(new UrlWatcher());

        Button update = (Button)repoView.findViewById(R.id.btn_update);
        update.setOnClickListener(new UpdateListener());

        return repoView;
    }

    /**
     * Populates relevant views with properties from the current repository.
     * Decides which views to show and hide depending on the state of the
     * repository.
     */
    private void updateView(ViewGroup repoView) {

        EditText inputUrl    = (EditText)repoView.findViewById(R.id.input_repo_url);
        TextView name        = (TextView)repoView.findViewById(R.id.text_repo_name);
        TextView numApps     = (TextView)repoView.findViewById(R.id.text_num_apps);
        TextView lastUpdated = (TextView)repoView.findViewById(R.id.text_last_update);
        TextView description = (TextView)repoView.findViewById(R.id.text_description);
        TextView signature   = (TextView)repoView.findViewById(R.id.text_signature);
        TextView signatureInfo = (TextView)repoView.findViewById(R.id.text_signature_description);

        boolean hasBeenUpdated = repo.lastetag != null;
        int showIfExists = hasBeenUpdated ? View.VISIBLE : View.GONE;
        int hideIfExists = hasBeenUpdated ? View.GONE : View.VISIBLE;

        for (int id : SHOW_IF_EXISTS) {
            repoView.findViewById(id).setVisibility(showIfExists);
        }

        for (int id : HIDE_IF_EXISTS) {
            repoView.findViewById(id).setVisibility(hideIfExists);
        }

        inputUrl.setText(repo.address);
        name.setText(repo.getName());
        numApps.setText(Integer.toString(repo.getNumberOfApps()));
        description.setText(repo.description);
        setupSignature(repo, signature, signatureInfo);

        if (repo.lastUpdated != null) {
            lastUpdated.setText(repo.lastUpdated.toString());
        } else {
            lastUpdated.setText(getString(R.string.unknown));
        }
    }

    /**
     * When the update button is clicked, notify the listener so that the repo
     * list can be updated. We will perform the update ourselves though.
     */
    class UpdateListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            UpdateService.updateNow(getActivity()).setListener(updateProgressListener);
            if (repoChangeListener != null) {
                repoChangeListener.onUpdatePerformed(repo);
            }
        }
    }

    /**
     * When the URL is changed, notify the repoChangeListener.
     */
    class UrlWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (repoChangeListener != null) {
                repoChangeListener.onRepoDetailsChanged(repo);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        MenuItem delete = menu.add(Menu.NONE, DELETE, 0, R.string.delete);
        delete.setIcon(android.R.drawable.ic_menu_delete);
        MenuItemCompat.setShowAsAction(delete,
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM |
            MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == DELETE) {
            promptForDelete();
            return true;
        }

        return false;
    }

    private void promptForDelete() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.repo_confirm_delete_title)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setMessage(R.string.repo_confirm_delete_body)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (repoChangeListener != null) {
                    DB.Repo repo = RepoDetailsFragment.this.repo;
                    repoChangeListener.onDeleteRepo(repo);
                }
            }
        }).setNegativeButton(android.R.string.cancel,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing...
                }
            }
        ).show();
    }

    private void setupSignature(DB.Repo repo, TextView signatureView,
                                TextView signatureDescView) {
        String signature;
        String signatureDesc;
        int signatureColour;
        if (repo.pubkey != null && repo.pubkey.length() > 0) {
            signature       = Utils.formatFingerprint(repo.pubkey);
            signatureDesc   = "";
            signatureColour = getResources().getColor(R.color.signed);
        } else {
            signature       = getResources().getString(R.string.unsigned);
            signatureDesc   = getResources().getString(R.string.unsigned_description);
            signatureColour = getResources().getColor(R.color.unsigned);
        }
        signatureView.setText(signature);
        signatureView.setTextColor(signatureColour);
        signatureDescView.setText(signatureDesc);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

}
