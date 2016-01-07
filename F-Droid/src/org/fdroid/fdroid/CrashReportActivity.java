package org.fdroid.fdroid;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.widget.EditText;

import org.acra.BaseCrashReportDialog;

public class CrashReportActivity extends BaseCrashReportDialog implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener {

    private EditText comment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.crash_dialog_title)
                .setView(R.layout.crash_report_dialog)
                .setPositiveButton(R.string.ok, this)
                .setNegativeButton(R.string.cancel, this)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(this);
        dialog.show();

        comment = (EditText) dialog.findViewById(android.R.id.input);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            sendCrash(comment.getText().toString(), "");
        } else {
            cancelReports();
        }
        finish();
    }

}
