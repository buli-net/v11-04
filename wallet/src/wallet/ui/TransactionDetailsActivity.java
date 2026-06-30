package wallet.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.Wallet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import wallet.R;
import wallet.WalletApplication;

public class TransactionDetailsActivity extends Activity {
    private TextView tvDirection, tvAmount, tvStatus, tvFee, tvTime, tvFrom, tvTo, tvTxid, tvHeight, tvMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle("Transaction Details");
        }

        tvDirection = findViewById(R.id.tv_direction);
        tvAmount = findViewById(R.id.tv_amount);
        tvStatus = findViewById(R.id.tv_status);
        tvFee = findViewById(R.id.tv_fee);
        tvTime = findViewById(R.id.tv_time);
        tvFrom = findViewById(R.id.tv_from);
        tvTo = findViewById(R.id.tv_to);
        tvTxid = findViewById(R.id.tv_txid);
        tvHeight = findViewById(R.id.tv_height);
        tvMeta = findViewById(R.id.tv_meta);

        String txidStr = getIntent().getStringExtra("txid");
        if (txidStr == null) { Toast.makeText(this, "Missing txid", Toast.LENGTH_SHORT).show(); finish(); return; }

        WalletApplication app = (WalletApplication) getApplication();
        Wallet wallet = app.getWallet();
        if (wallet == null) { Toast.makeText(this, "Wallet not ready", Toast.LENGTH_SHORT).show(); finish(); return; }
        NetworkParameters params = wallet.getNetworkParameters();

        Transaction tx;
        try {
            tx = wallet.getTransaction(Sha256Hash.wrap(txidStr));
        } catch (Exception e) { tx = null; }
        if (tx == null) { Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show(); finish(); return; }

        Coin value = Coin.ZERO;
        try { Coin v = tx.getValue(wallet); if (v != null) value = v; } catch (Exception ignored) {}
        boolean isSend = value.isNegative();
        Coin absValue = isSend ? value.negate() : value;

        tvDirection.setText(isSend ? "Sent" : "Received");
        tvAmount.setText((isSend ? "-" : "+") + absValue.toPlainString() + " BTC");
        try {
            tvAmount.setTextColor(getResources().getColor(isSend ? R.color.tx_amount_sent : R.color.tx_amount_recv));
        } catch (Exception ignored) {}

        // Status 3 levels: Pending / Building / Confirmed
        TransactionConfidence confidence = tx.getConfidence();
        int depth = 0;
        int height = 0;
        if (confidence != null) {
            try { depth = confidence.getDepthInBlocks(); } catch (Exception ignored) {}
            try { height = confidence.getAppearedAtChainHeight(); } catch (Exception ignored) {}
        }

        String statusText;
        int statusColorRes;
        if (depth <= 0) {
            statusText = "Pending";
            statusColorRes = R.color.tx_status_pending;
        } else if (depth < 6) {
            statusText = "Building";
            statusColorRes = R.color.tx_status_building;
        } else {
            statusText = "Confirmed";
            statusColorRes = R.color.tx_status_ok;
        }
        tvStatus.setText(statusText);
        try {
            tvStatus.setTextColor(getResources().getColor(statusColorRes));
        } catch (Exception ignored) {}

        Coin fee = null;
        try { fee = tx.getFee(); } catch (Exception ignored) {}
        tvFee.setText(fee != null ? fee.toPlainString() + " BTC" : "—");

        Date updateTime = null;
        try { updateTime = tx.getUpdateTime(); } catch (Exception ignored) {}
        if (updateTime != null) {
            tvTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(updateTime));
        } else {
            tvTime.setText("—");
        }

        // Confirmations - live real count
        String confStr;
        if (depth <= 0) {
            confStr = "unconfirmed";
        } else {
            confStr = depth + " confirmations";
        }
        if (height > 0) {
            confStr += " · height " + height;
        }
        tvHeight.setText(confStr);

        int size = 0, weight = 0;
        boolean rbf = false;
        try { size = tx.getMessageSize(); } catch (Exception ignored) {}
        try { weight = tx.getWeight(); } catch (Exception ignored) {}
        try { rbf = tx.isOptInFullRBF(); } catch (Exception ignored) {}
        
        String feeRate = "";
        if (fee != null && weight > 0) {
            try {
                long satPerVbyte = fee.getValue() * 4 / weight;
                feeRate = " · " + satPerVbyte + " sat/vB";
            } catch (Exception ignored) {}
        }
        tvMeta.setText(size + " bytes · " + weight + " wu" + feeRate + (rbf ? " · RBF" : ""));

        // ===== FROM / TO full list - fix trên file gốc =====
        StringBuilder fromSb = new StringBuilder();
        Coin totalFrom = Coin.ZERO;
        int inCount = 0;
        if (tx.getInputs() != null) {
            for (TransactionInput in : tx.getInputs()) {
                inCount++;
                Coin v = null;
                String addr = "unknown";
                String type = "nonstandard";
                try {
                    TransactionOutPoint outpoint = in.getOutpoint();
                    if (outpoint != null && outpoint.getConnectedOutput() != null) {
                        TransactionOutput connected = outpoint.getConnectedOutput();
                        v = connected.getValue();
                        addr = getAddressFromScript(connected.getScriptPubKey(), params);
                        if (addr == null) addr = "unknown";
                        type = getAddressType(addr, connected.getScriptPubKey());
                    }
                } catch (Exception ignored) {}
                if (v != null) totalFrom = totalFrom.add(v);
                fromSb.append(addr).append(" (").append(type).append(") - ")
                      .append(v != null ? v.toPlainString() + " BTC" : "? BTC").append("\n");
            }
        }
        String fromText = "Total: " + totalFrom.toPlainString() + " BTC from " + inCount + "\n" + fromSb.toString().trim();
        
        StringBuilder toSb = new StringBuilder();
        Coin totalTo = Coin.ZERO;
        int outCount = tx.getOutputs() != null ? tx.getOutputs().size() : 0;
        if (tx.getOutputs() != null) {
            for (TransactionOutput out : tx.getOutputs()) {
                Coin v = out.getValue();
                if (v != null) totalTo = totalTo.add(v);
                String addr = getAddressFromScript(out.getScriptPubKey(), params);
                if (addr == null) addr = "unknown";
                String type = getAddressType(addr, out.getScriptPubKey());
                toSb.append(addr).append(" (").append(type).append(") - ")
                    .append(v != null ? v.toPlainString() + " BTC" : "? BTC").append("\n");
            }
        }
        String toText = "Total: " + totalTo.toPlainString() + " BTC to " + outCount + "\n" + toSb.toString().trim();

        tvFrom.setSingleLine(false);
        tvTo.setSingleLine(false);
        tvFrom.setText(fromText);
        tvTo.setText(toText);
        copyOnClick(tvFrom, fromText);
        copyOnClick(tvTo, toText);
        // ===== end fix =====

        String hash = tx.getTxId().toString();
        tvTxid.setText(hash);
        copyOnClick(tvTxid, hash);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String getAddressFromScript(Script script, NetworkParameters params) {
        if (script == null) return null;
        try {
            return script.getToAddress(params).toString();
        } catch (ScriptException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // thêm helper detect type, giống code trong WalletTransactionsFragment của mày
    private String getAddressType(String addr, Script script) {
        try {
            if (script != null && ScriptPattern.isOpReturn(script)) return "OP_RETURN";
        } catch (Exception ignored) {}
        if (addr == null) return "nonstandard";
        if (addr.startsWith("bc1q") || addr.startsWith("tb1q")) return "P2WPKH";
        if (addr.startsWith("bc1p") || addr.startsWith("tb1p")) return "P2TR";
        if (addr.startsWith("bc1") || addr.startsWith("tb1")) return "P2WSH";
        if (addr.startsWith("3") || addr.startsWith("2")) return "P2SH";
        if (addr.startsWith("1") || addr.startsWith("m") || addr.startsWith("n")) return "P2PKH";
        return "nonstandard";
    }

    private String getInputAddress(Transaction tx, NetworkParameters params, Wallet wallet, Boolean mineOnly) {
        if (tx.getInputs() == null) return null;
        for (TransactionInput in : tx.getInputs()) {
            try {
                TransactionOutPoint outpoint = in.getOutpoint();
                if (outpoint != null && outpoint.getConnectedOutput() != null) {
                    TransactionOutput connected = outpoint.getConnectedOutput();
                    if (mineOnly != null) {
                        boolean isMine;
                        try { isMine = connected.isMine(wallet); } catch (Exception e) { continue; }
                        if (isMine != mineOnly) continue;
                    }
                    String a = getAddressFromScript(connected.getScriptPubKey(), params);
                    if (a != null) return a;
                }
                if (mineOnly == null) {
                    try {
                        String a = getAddressFromScript(in.getScriptSig(), params);
                        if (a != null) return a;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getOutputAddress(Transaction tx, NetworkParameters params, Wallet wallet, Boolean mineOnly) {
        if (tx.getOutputs() == null) return null;
        for (TransactionOutput out : tx.getOutputs()) {
            try {
                if (mineOnly != null) {
                    boolean isMine;
                    try { isMine = out.isMine(wallet); } catch (Exception e) { continue; }
                    if (isMine != mineOnly) continue;
                }
                String a = getAddressFromScript(out.getScriptPubKey(), params);
                if (a != null) return a;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void copyOnClick(TextView tv, String text) {
        if (tv == null) return;
        tv.setOnClickListener(v -> copy(text));
    }
    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("tx", text));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }
}
