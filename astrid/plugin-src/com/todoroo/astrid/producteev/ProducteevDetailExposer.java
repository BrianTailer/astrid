/**
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.producteev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.astrid.adapter.TaskAdapter;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.api.DetailExposer;
import com.todoroo.astrid.model.Metadata;
import com.todoroo.astrid.model.StoreObject;
import com.todoroo.astrid.producteev.sync.ProducteevDashboard;
import com.todoroo.astrid.producteev.sync.ProducteevDataService;
import com.todoroo.astrid.producteev.sync.ProducteevNote;
import com.todoroo.astrid.producteev.sync.ProducteevTask;
import com.todoroo.astrid.utility.Preferences;

/**
 * Exposes Task Details for Producteev:
 * - notes
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class ProducteevDetailExposer extends BroadcastReceiver implements DetailExposer{

    @Override
    public void onReceive(Context context, Intent intent) {
        // if we aren't logged in, don't expose features
        if(!ProducteevUtilities.INSTANCE.isLoggedIn())
            return;

        long taskId = intent.getLongExtra(AstridApiConstants.EXTRAS_TASK_ID, -1);
        if(taskId == -1)
            return;

        boolean extended = intent.getBooleanExtra(AstridApiConstants.EXTRAS_EXTENDED, false);
        String taskDetail = getTaskDetails(context, taskId, extended);
        if(taskDetail == null)
            return;

        Intent broadcastIntent = new Intent(AstridApiConstants.BROADCAST_SEND_DETAILS);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_ADDON, ProducteevUtilities.IDENTIFIER);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_TASK_ID, taskId);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_EXTENDED, extended);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_RESPONSE, taskDetail);
        context.sendBroadcast(broadcastIntent, AstridApiConstants.PERMISSION_READ);
    }

    @Override
    public String getTaskDetails(Context context, long id, boolean extended) {
        Metadata metadata = ProducteevDataService.getInstance().getTaskMetadata(id);
        if(metadata == null)
            return null;

        StringBuilder builder = new StringBuilder();

        if(!extended) {
            long dashboardId = metadata.getValue(ProducteevTask.DASHBOARD_ID);
            long responsibleId = -1;
            if(metadata.containsNonNullValue(ProducteevTask.RESPONSIBLE_ID))
                responsibleId = metadata.getValue(ProducteevTask.RESPONSIBLE_ID);

            // display dashboard if not "no sync" or "default"
            StoreObject ownerDashboard = null;
            for(StoreObject dashboard : ProducteevDataService.getInstance().getDashboards()) {
                if(dashboard.getValue(ProducteevDashboard.REMOTE_ID) == dashboardId) {
                    ownerDashboard = dashboard;
                    break;
                }
            }
            if(dashboardId != ProducteevUtilities.DASHBOARD_NO_SYNC && dashboardId
                    != Preferences.getLong(ProducteevUtilities.PREF_DEFAULT_DASHBOARD, 0L) &&
                    ownerDashboard != null) {
                String dashboardName = ownerDashboard.getValue(ProducteevDashboard.NAME);
                builder.append("<img src='silk_script'/> ").append(dashboardName).append(TaskAdapter.DETAIL_SEPARATOR); //$NON-NLS-1$
            }

            // display responsible user if not current one
            if(responsibleId > 0 && ownerDashboard != null && responsibleId !=
                    Preferences.getLong(ProducteevUtilities.PREF_USER_ID, 0L)) {
                String users = ";" + ownerDashboard.getValue(ProducteevDashboard.USERS); //$NON-NLS-1$
                int index = users.indexOf(";" + responsibleId + ","); //$NON-NLS-1$ //$NON-NLS-2$
                if(index > -1) {
                    String user = users.substring(users.indexOf(',', index) + 1,
                            users.indexOf(';', index + 1));
                    builder.append("<img src='silk_user_gray'/> ").append(user).append(TaskAdapter.DETAIL_SEPARATOR); //$NON-NLS-1$
                }
            }
        } else {
            TodorooCursor<Metadata> notesCursor = ProducteevDataService.getInstance().getTaskNotesCursor(id);
            try {
                for(notesCursor.moveToFirst(); !notesCursor.isAfterLast(); notesCursor.moveToNext()) {
                    metadata.readFromCursor(notesCursor);
                    builder.append(metadata.getValue(ProducteevNote.MESSAGE)).append(TaskAdapter.DETAIL_SEPARATOR);
                }
            } finally {
                notesCursor.close();
            }
        }

        if(builder.length() == 0)
            return null;
        String result = builder.toString();
        return result.substring(0, result.length() - TaskAdapter.DETAIL_SEPARATOR.length());
    }

    @Override
    public String getPluginIdentifier() {
        return ProducteevUtilities.IDENTIFIER;
    }

}