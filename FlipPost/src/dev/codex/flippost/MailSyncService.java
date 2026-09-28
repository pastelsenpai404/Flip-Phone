package dev.codex.flippost;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class MailSyncService extends JobService {
    private Thread work;
    static void schedule(Context context){
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobs.cancel(601);
        if(!MailClient.configured(context)||!MailClient.prefs(context).getBoolean("mail_poll",false))return;
        jobs.schedule(new JobInfo.Builder(601,new ComponentName(context,MailSyncService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60*1000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(final JobParameters parameters){
        if(!MailClient.configured(this)||!MailClient.prefs(this).getBoolean("mail_poll",false))return false;
        work=new Thread(new Runnable(){public void run(){
            try{MailClient.sync(MailSyncService.this,true);}catch(Exception ignored){}
            if(!Thread.currentThread().isInterrupted())jobFinished(parameters,false);
        }},"FlipPostMailSync");work.start();return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){if(work!=null)work.interrupt();return true;}
}
