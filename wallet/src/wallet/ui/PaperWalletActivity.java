package wallet.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.print.PrintHelper;

import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.NetworkParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import wallet.Constants;
import wallet.R;
import wallet.util.Qr;

public class PaperWalletActivity extends AbstractWalletActivity {
    private static final int QR_SIZE = 512;

    private View cardView;
    private ImageView qrAddressView, qrKeyView;
    private TextView addressView, pubKeyView, privKeyView, addressTypeView;
    private Button toggleKeyButton;
    private boolean keyVisible = true;

    private String currentAddress = "";
    private String currentPubKey = "";
    private String currentPrivKey = "";

    // SegWit bech32 bc1q... | đổi thành ScriptType.P2PKH nếu muốn Legacy 1...
    private ScriptType addressType = ScriptType.P2WPKH;

    private String getFileProviderAuthority() {
        return getPackageName() + ".file_attachment";
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paper_wallet);

        if (getActionBar() != null)
            getActionBar().setDisplayHomeAsUpEnabled(true);

        cardView = findViewById(R.id.paper_wallet_card);
        qrAddressView = findViewById(R.id.paper_wallet_qr_address);
        qrKeyView = findViewById(R.id.paper_wallet_qr_key);
        addressView = findViewById(R.id.paper_wallet_address);
        pubKeyView = findViewById(R.id.paper_wallet_pubkey);
        privKeyView = findViewById(R.id.paper_wallet_key);
        addressTypeView = findViewById(R.id.paper_wallet_address_type);

        if (qrAddressView != null) qrAddressView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (qrKeyView != null) qrKeyView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        findViewById(R.id.paper_wallet_copy_address).setOnClickListener(v -> copyText("Address", currentAddress));
        findViewById(R.id.paper_wallet_copy_pubkey).setOnClickListener(v -> copyText("Public key", currentPubKey));
        findViewById(R.id.paper_wallet_copy_privkey).setOnClickListener(v -> copyText("Private key", currentPrivKey));

        toggleKeyButton = findViewById(R.id.paper_wallet_toggle_key);
        toggleKeyButton.setOnClickListener(v -> toggleKeyVisibility());

        findViewById(R.id.paper_wallet_generate).setOnClickListener(v -> generateNew());
        findViewById(R.id.paper_wallet_save).setOnClickListener(v -> savePaperWallet());
        findViewById(R.id.paper_wallet_share).setOnClickListener(v -> sharePaperWallet());
        
        View printBtn = findViewById(R.id.paper_wallet_print);
        if (printBtn != null) {
            printBtn.setVisibility(View.VISIBLE);
            printBtn.setOnClickListener(v -> printPaperWallet());
        }

        if (addressTypeView != null) {
            addressTypeView.setOnClickListener(v -> {
                addressType = (addressType == ScriptType.P2PKH) ? ScriptType.P2WPKH : ScriptType.P2PKH;
                generateNew();
            });
        }

        generateNew();
    }

    private Network getNetwork() {
        NetworkParameters params = Constants.NETWORK_PARAMETERS;
        String id = params.getId().toLowerCase();
        if (id.contains("regtest")) return BitcoinNetwork.REGTEST;
        if (id.contains("test")) return BitcoinNetwork.TESTNET;
        return BitcoinNetwork.MAINNET;
    }

    private Bitmap makeQr(String text) {
        Bitmap qr = Qr.bitmap(text);
        return Bitmap.createScaledBitmap(qr, QR_SIZE, QR_SIZE, false);
    }

    private void generateNew() {
        final Network network = getNetwork();
        final ECKey key = new ECKey();

        currentAddress = key.toAddress(addressType, network).toString();
        currentPubKey = key.getPublicKeyAsHex();
        currentPrivKey = key.getPrivateKeyAsWiF(network);

        addressView.setText(currentAddress);
        pubKeyView.setText(currentPubKey);
        updatePrivKeyView();

        if (addressTypeView != null) {
            String label = (addressType == ScriptType.P2PKH) ? "Legacy P2PKH (1...)" : "SegWit bech32 (bc1q...) - tap to switch";
            addressTypeView.setText(label);
        }

        qrAddressView.setImageBitmap(makeQr(currentAddress));
        qrKeyView.setImageBitmap(makeQr(currentPrivKey));

        Toast.makeText(this, R.string.paper_wallet_generated, Toast.LENGTH_SHORT).show();
    }

    private void updatePrivKeyView() {
        if (keyVisible) {
            privKeyView.setText(currentPrivKey);
            toggleKeyButton.setText(R.string.paper_wallet_hide_key);
        } else {
            privKeyView.setText("••••••••••••••••••••••••");
            toggleKeyButton.setText(R.string.paper_wallet_show_key);
        }
    }

    private void toggleKeyVisibility() {
        keyVisible = !keyVisible;
        updatePrivKeyView();
    }

    private void copyText(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private Bitmap renderCard() {
        Bitmap bitmap = Bitmap.createBitmap(cardView.getWidth(), cardView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cardView.draw(canvas);
        return bitmap;
    }

    private File getShareFile() throws Exception {
        File dir = new File(getCacheDir(), "paperwallet");
        dir.mkdirs();
        File file = new File(dir, "paperwallet_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            renderCard().compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private void savePaperWallet() {
        try {
            Bitmap bitmap = renderCard();
            String filename = "paperwallet_" + System.currentTimeMillis() + ".png";
            
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaperWallet");
            
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText(this, "Saved to Pictures/PaperWallet/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePaperWallet() {
        try {
            File file = getShareFile();
            Uri uri = FileProvider.getUriForFile(this, getFileProviderAuthority(), file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.paper_wallet_share)));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void printPaperWallet() {
        try {
            PrintHelper helper = new PrintHelper(this);
            helper.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            helper.printBitmap("Paper Wallet - " + currentAddress, renderCard());
        } catch (Exception e) {
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
