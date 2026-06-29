package wallet.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import wallet.Constants;
import wallet.R;
import wallet.WalletApplication;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.base.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class TransactionDetailsActivity extends Activity {

    public static final String EXTRA_TX_HASH = "transaction_hash";
    private Wallet wallet;
    private Transaction tx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setTitle("Transaction Details");
        }

        WalletApplication application = (WalletApplication) getApplication();
        wallet = application.getWallet();

        String txHash = getIntent().getStringExtra(EXTRA_TX_HASH);
        if (txHash == null) { finish(); return; }

        try {
            tx = wallet.getTransaction(Sha256Hash.wrap(txHash));
        } catch (Exception ignored) {}

        if (tx == null) {
            Toast.makeText(this, "Tx not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindTransaction(tx);

        TextView txIdView = findViewById(R.id.tx_id);
        txIdView.setText(tx.getTxId().toString());
        txIdView.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("txid", tx.getTxId().toString()));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    private void bindTransaction(Transaction tx) {
        Coin value = Coin.ZERO;
        try { value = tx.getValue(wallet); } catch (Exception ignored) {}
        boolean isSent = value.signum() < 0;

        TextView amountView = findViewById(R.id.tx_amount);
        TextView dirView = findViewById(R.id.tx_direction_label);
        dirView.setText(isSent ? "Sent" : "Received");
        amountView.setText(value.toFriendlyString());
        amountView.setTextColor(ContextCompat.getColor(this,
                isSent ? R.color.tx_amount_sent : R.color.tx_amount_recv));

        int confs = 0;
        try { if (tx.getConfidence() != null) confs = tx.getConfidence().getDepthInBlocks(); } catch (Exception ignored) {}
        TextView statusView = findViewById(R.id.tx_status);
        if (confs >= 6) {
            statusView.setText("Confirmed");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.tx_status_ok));
        } else if (confs > 0) {
            statusView.setText("Building");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.tx_status_building));
        } else {
            statusView.setText("Pending");
            statusView.setTextColor(ContextCompat.getColor(this, R.color.tx_status_pending));
        }

        Coin fee = null;
        try { fee = tx.getFee(); } catch (Exception ignored) {}
        ((TextView)findViewById(R.id.tx_fee)).setText(fee != null ? fee.toFriendlyString() : "—");

        int size = tx.getMessageSize();
        int weight = tx.getWeight();
        long vsize = (weight + 3) / 4;
        long feePerVb = fee != null && vsize > 0 ? fee.toSat() / vsize : 0;
        ((TextView)findViewById(R.id.tx_size)).setText(size + " bytes · " + weight + " wu · " + feePerVb + " sat/vB");

        ((TextView)findViewById(R.id.tx_confirmations)).setText(confs + " confirmations");

        try {
            java.util.Date time = tx.getUpdateTime();
            if (time != null) {
                String s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(time);
                ((TextView)findViewById(R.id.tx_time)).setText(s);
            }
        } catch (Exception ignored) {}

        renderInputsOutputs(tx);
    }

    private void renderInputsOutputs(Transaction tx) {
        LinearLayout fromContainer = findViewById(R.id.from_container);
        TextView fromSummary = findViewById(R.id.from_summary);
        LinearLayout toContainer = findViewById(R.id.to_container);
        TextView toSummary = findViewById(R.id.to_summary);

        fromContainer.removeAllViews();
        toContainer.removeAllViews();

        Coin totalIn = Coin.ZERO;
        int inCount = tx.getInputs().size();
        for (TransactionInput input : tx.getInputs()) {
            Coin value = null;
            String addr = "unknown";
            String type = "nonstandard";
            try {
                TransactionOutput c = input.getConnectedOutput();
                if (c != null) {
                    value = c.getValue();
                    try { addr = c.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS).toString(); }
                    catch (Exception e1) { addr = c.getScriptPubKey().getToAddress(org.bitcoinj.params.TestNet3Params.get()).toString(); }
                    type = getScriptType(addr);
                }
            } catch (Exception ignored) {}
            if (value != null) totalIn = totalIn.add(value);
            addIoRow(fromContainer, addr, type, value);
        }
        fromSummary.setText("Total: " + totalIn.toFriendlyString() + " from " + inCount);

        Coin totalOut = Coin.ZERO;
        int outCount = tx.getOutputs().size();
        for (TransactionOutput out : tx.getOutputs()) {
            Coin value = out.getValue();
            if (value != null) totalOut = totalOut.add(value);
            String addr = "unknown";
            String type = "nonstandard";
            try {
                if (ScriptPattern.isOpReturn(out.getScriptPubKey())) {
                    type = "OP_RETURN"; addr = "OP_RETURN";
                } else {
                    Address a = null;
                    try { a = out.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS); }
                    catch (Exception e1) { a = out.getScriptPubKey().getToAddress(org.bitcoinj.params.TestNet3Params.get()); }
                    if (a != null) { addr = a.toString(); type = getScriptType(addr); }
                }
            } catch (Exception ignored) {}
            addIoRow(toContainer, addr, type, value);
        }
        toSummary.setText("Total: " + totalOut.toFriendlyString() + " to " + outCount);
    }

    private void addIoRow(LinearLayout container, String address, String type, Coin value) {
        TextView tv = new TextView(this);
        String valStr = value != null ? value.toFriendlyString() : "? BTC";
        tv.setText(address + " (" + type + ") - " + valStr);
        tv.setTextColor(ContextCompat.getColor(this, R.color.tx_text_primary));
        tv.setTextSize(13);
        tv.setPadding(0, 12, 0, 12);
        container.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String getScriptType(String addr) {
        if (addr.startsWith("bc1q") || addr.startsWith("tb1q")) return "P2WPKH";
        if (addr.startsWith("bc1p") || addr.startsWith("tb1p")) return "P2TR";
        if (addr.startsWith("bc1") || addr.startsWith("tb1")) return "P2WSH";
        if (addr.startsWith("3") || addr.startsWith("2")) return "P2SH";
        if (addr.startsWith("1") || addr.startsWith("m") || addr.startsWith("n")) return "P2PKH";
        return "nonstandard";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
