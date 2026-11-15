package it.fototag.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_JPEG = 100;
    private static final int BG = Color.rgb(248,249,251);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LinkedHashSet<String> tags = new LinkedHashSet<>();
    private Uri uri;
    private File sourceCache;
    private TextView status, filename, count;
    private EditText input;
    private LinearLayout tagContainer;
    private ImageView preview;
    private Button save;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(26),dp(20),dp(28));
        scroll.addView(root);
        TextView heading = text("FotoTag", 29, true); root.addView(heading);
        root.addView(text("Tag IPTC/XMP direttamente nei file JPEG",14,false));
        Button open=button("1. Scegli una foto JPEG"); root.addView(open);
        open.setOnClickListener(v -> selectPhoto());
        filename=text("Nessuna fotografia selezionata",15,true); root.addView(filename);
        preview=new ImageView(this); preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1,dp(190));
        imageParams.topMargin=dp(12); root.addView(preview,imageParams);
        count=text("0 tag",17,true); root.addView(count);
        tagContainer=new LinearLayout(this); tagContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(tagContainer);
        input=new EditText(this);
        input.setHint("Aggiungi tag, separati da virgola o a capo");
        input.setMinLines(2); input.setMaxLines(5);
        root.addView(input);
        Button add=button("2. Aggiungi tag"); root.addView(add);
        add.setOnClickListener(v->{
            for(String item: input.getText().toString().split("[,;\\n]+")) {
                String t=item.trim(); if(!t.isEmpty() && t.length()<=120) tags.add(t);
            }
            input.setText(""); refreshTags();
        });
        Button examples=button("Aggiungi 30 tag di esempio"); root.addView(examples);
        examples.setOnClickListener(v->{
            tags.addAll(Arrays.asList("Famiglia","Vacanze","Italia","Spagna","Francia","2026","2025","MTB","Moto","Bambini","Paesaggio","Montagna","Mare","Spiaggia","Lago","Città","Centro storico","Architettura","Animali","Gatto","Tramonto","Alba","Ritratto","Sport","Natura","Bosco","Viaggio","Camper","Fotografia notturna","Canon R50"));
            refreshTags();
        });
        save=button("3. Salva tag nella foto originale"); root.addView(save);
        save.setEnabled(false); save.setOnClickListener(v->confirmSave());
        status=text("La foto viene modificata solo dopo la conferma. Esegui una copia di sicurezza prima del primo test.",13,false);
        root.addView(status);
        root.addView(text("Versione prototipo: JPEG soltanto. I tag sono metadati XMP standard. La ricerca nella Galleria Samsung va verificata sul dispositivo.",12,false));
        setContentView(scroll);
        refreshTags();
    }
    private TextView text(String value,int sp,boolean bold) {
        TextView tv=new TextView(this); tv.setText(value);tv.setTextSize(sp);tv.setTextColor(Color.rgb(30,41,59));
        if(bold)tv.setTypeface(null,Typeface.BOLD);
        tv.setPadding(0,dp(8),0,dp(8));return tv;
    }
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);return b;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void refreshTags(){
        count.setText(tags.size()+" tag assegnati");tagContainer.removeAllViews();
        for(String tag:new ArrayList<>(tags)){
            Button pill=button(tag+"    ×"); pill.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tagContainer.addView(pill);
            pill.setOnClickListener(v->{tags.remove(tag); refreshTags();});
        }
    }
    private void selectPhoto(){
        Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("image/jpeg");
        in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(in,PICK_JPEG);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_JPEG || result!=RESULT_OK || data==null || data.getData()==null)return;
        uri=data.getData();
        int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Exception ignored){}
        filename.setText(getName(uri));
        status.setText("Lettura della fotografia...");save.setEnabled(false);
        executor.execute(()->{
            try{
                File temp=File.createTempFile("fototag_original_",".jpg",getCacheDir());
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IllegalStateException("Impossibile leggere il file");copy(in,out);
                }
                List<String> found=JpegXmpEditor.readKeywords(temp);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(temp.getAbsolutePath(),options);
                options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/900);
                options.inJustDecodeBounds=false;
                Bitmap bmp=BitmapFactory.decodeFile(temp.getAbsolutePath(),options);
                runOnUiThread(()->{
                    if(sourceCache!=null)sourceCache.delete();sourceCache=temp;
                    tags.clear();tags.addAll(found);refreshTags();preview.setImageBitmap(bmp);
                    status.setText("Foto caricata. Tag XMP esistenti: "+found.size());save.setEnabled(true);
                });
            }catch(Exception e){runOnUiThread(()->status.setText("Errore lettura: "+e.getMessage()));}
        });
    }
    private String getName(Uri u){
        try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}return u.getLastPathSegment();
    }
    private void confirmSave(){
        if(uri==null||sourceCache==null)return;
        new AlertDialog.Builder(this).setTitle("Modificare la foto originale?")
            .setMessage("I tag saranno scritti nel JPEG selezionato. Consigliata una copia di sicurezza della foto prima del test. Continuare?")
            .setNegativeButton("Annulla",null).setPositiveButton("Salva",(d,w)->savePhoto()).show();
    }
    private void savePhoto(){
        save.setEnabled(false);status.setText("Scrittura metadati in corso...");
        List<String> newTags=new ArrayList<>(tags);
        Uri target=uri; File backup=sourceCache;
        executor.execute(()->{
            File updated=null;
            try{
                updated=File.createTempFile("fototag_updated_",".jpg",getCacheDir());
                JpegXmpEditor.writeKeywords(backup,updated,newTags);
                // Un documento SAF potrebbe non supportare la scrittura. Non intervenire senza permesso.
                try(OutputStream out=getContentResolver().openOutputStream(target,"wt"); InputStream in=new FileInputStream(updated)){
                    if(out==null)throw new IllegalStateException("Il provider non permette modifiche al file");copy(in,out);
                }catch(Exception writeError){
                    // Tentativo di ripristino tramite la copia integrale originale conservata in cache.
                    try(OutputStream out=getContentResolver().openOutputStream(target,"wt");InputStream in=new FileInputStream(backup)){
                        if(out!=null)copy(in,out);
                    }catch(Exception restoreError){
                        throw new IllegalStateException("Scrittura fallita; ripristino automatico non riuscito. Conserva la copia di backup dell'originale.",restoreError);
                    }
                    throw writeError;
                }
                File result=updated;
                runOnUiThread(()->{
                    if(sourceCache!=null)sourceCache.delete();sourceCache=result;
                    status.setText("Salvati "+newTags.size()+" tag XMP nel JPEG originale. Verifica le parole chiave su Windows e in Galleria Samsung.");
                    save.setEnabled(true);
                    Toast.makeText(this,"Tag salvati",Toast.LENGTH_LONG).show();
                });updated=null;
            }catch(Exception e){runOnUiThread(()->{status.setText("Salvataggio non riuscito: "+e.getMessage());save.setEnabled(true);});}
            finally{if(updated!=null)updated.delete();}
        });
    }
    private static void copy(InputStream in,OutputStream out)throws java.io.IOException{
        byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
    }
    @Override protected void onDestroy(){executor.shutdown();super.onDestroy();}
}
