import models.vo.Transaction;
import models.vo.VccProof;
import org.junit.Test;
import utils.ECKeyPair;
import utils.thkUtils;

import java.math.BigInteger;
import java.util.Map;

import static web3.Thk.log;

/**
 * @author 乔健勇
 * @date 15:50 2020/7/3
 * @email qjyoung@163.com
 */
public class TransferAmongChainsTest {
    private static web3.Thk web3;
    private static String SYSTEM_CONTRACT_ADDRESS_DEPOSIT = "0x0000000000000000000000000000000000020000"; // 系统跨链取款合约-取款到支票
    private static String SYSTEM_CONTRACT_ADDRESS_WITHDRAW = "0x0000000000000000000000000000000000030000"; // 系统跨链存款合约-从支票取款到账户
    private static String SYSTEM_CONTRACT_ADDRESS_CANCEL = "0x0000000000000000000000000000000000040000"; // 系统跨链撤销存款合约-从支票退回原账户
    
    private static int FROM_CHAIN_ID = 1;
    private static int TO_CHAIN_ID = 2;
    
    private static String PRIVATE_KEY_FROM = "0x8e5b44b6cee8fa05092b4b5a8843aa6b0ec37915a940c9b5938e88a7e6fdd83a";
    private static String FROM_ADDRESS = "0xf167a1c5c5fab6bddca66118216817af3fa86827";
    private static String TO_ADDRESS = "0xf167a1c5c5fab6bddca66118216817af3fa86827"; // 这里演示方便都用同一个账户
    private static String TEST_AMOUNT = "100000000000000000000";
    
    // 测试撤销支票时可设置小些
    private static int EXPIRE_AT = 200;
    
    static {
        web3 = new web3.Thk();
        web3.setUrl("http://rpctest.thinkium.org");
        thkUtils.setPrivateKey(PRIVATE_KEY_FROM);
    }
    
    // 1-首先从A链开支票，发送一笔交易到【系统跨链存款合约(0x0000000000000000000000000000000000020000)】, 需要指定支票过期高度，指的是当目标链高度超过（不含）这个值时，这张支票不能被支取，只能退回（从当前块高加上一个常数）
    // 2-检查上一步交易结果
    // 3-如果交易成功，从A链获取B链支票存款证明
    // 4-将返回的支票证明作为input，从B链发送一笔交易到【系统跨链存款合约(0x0000000000000000000000000000000000030000)】
    // 5-检查上步骤交易结果，如果交易成功则跨链转账成功
    
    // 达到指定块高之后才能被取消
    // 6-如果到达指定块高支票未被支取则需要手动取消跨链存款
    // 7-从B链获取支票撤销证明将之作为input，发送一笔交易到【系统跨链撤销存款合约(0x0000000000000000000000000000000000030000)】
    // 8-检查上步骤交易状态，如果失败可重试6、7、8步骤或者联系芯际技术协助处理
    
    /**
     * 生成支票
     */
    public VccProof genCheque() {
        log("genCheque()");
        final int height = getChainHeight(TO_CHAIN_ID + "");
        int expireHeight = height + EXPIRE_AT;
        log("genCheque-expireHeight", expireHeight);
        final int nonce = getNonce(FROM_CHAIN_ID + "", FROM_ADDRESS);
        log("genCheque-nonce", nonce);
        
        final VccProof vccProof = new VccProof();
        vccProof.setFromChain(FROM_CHAIN_ID);
        vccProof.setFromAddress(FROM_ADDRESS);
        vccProof.setNonce(nonce);
        vccProof.setToChain(TO_CHAIN_ID);
        vccProof.setToAddress(TO_ADDRESS);
        vccProof.setExpireHeight(expireHeight);
        vccProof.setAmount(new BigInteger(TEST_AMOUNT));
        log("genCheque-vccProof", vccProof);
        
        final String vccInput = vccProof.encode();
        log("genCheque-vccInput", vccInput);
        
        sendTx(FROM_CHAIN_ID, TO_CHAIN_ID, FROM_ADDRESS, SYSTEM_CONTRACT_ADDRESS_DEPOSIT, vccInput);
        
        return vccProof;
    }
    
    /**
     * 兑现支票
     */
    @Test
    public void testCashCheque() {
        EXPIRE_AT = 200;
        log("testCashCheque()");
        final VccProof vccProof = genCheque();
        Map proofOut = (Map) retryIfPending(() -> {
            Map proofIn = web3.RpcMakeVccProof(vccProof, true);
            if (proofIn.get("errCode") != null) {
                return null;
            }
            return proofIn;
        });
        log("testCashCheque-proof", proofOut);
        
        assert !isExpire(vccProof);
        
        final String input = ((String) proofOut.get("input"));
        log("testCashCheque-proof-input", input);
        sendTx(vccProof.getToChain(), vccProof.getToChain(), vccProof.getFromAddress(), SYSTEM_CONTRACT_ADDRESS_WITHDRAW, input);
    }
    
    /**
     * 撤销支票
     */
    @Test
    public void testCancelCheque() {
        EXPIRE_AT = 5;
        log("testCancelCheque()");
        final VccProof vccProof = genCheque();
        
        log("waiting until block height > expireHeight");
        retryIfPending(() -> isExpire(vccProof) ? 1 : null);
        
        // 生成取消支票的证明
        vccProof.setChainId(vccProof.getToChain()); // 设置为B链
        Map proof = web3.RpcMakeVccProof(vccProof, false);
        log("testCancelCheque-proof", proof);
        final String input = ((String) proof.get("input"));
        log("testCancelCheque-input", input);
        
        sendTx(vccProof.getFromChain(), vccProof.getFromChain(), vccProof.getFromAddress(), SYSTEM_CONTRACT_ADDRESS_CANCEL, input);
    }
    
    public Map sendTx(int fromChainId, int toChainId, String fromAddress, String toAddress, String input) {
        log("sendTx");
        String pub = thkUtils.GetPublicKey();
        ECKeyPair ecKeyPair = thkUtils.GetECKeyPair();
        
        Transaction tx = new Transaction();
        tx.setChainId(fromChainId + "");
        tx.setFromChainId(fromChainId + "");
        tx.setToChainId(toChainId + "");
        tx.setFrom(fromAddress);
        tx.setTo(toAddress);
        tx.setNonce(getNonce(tx));
        tx.setValue("0"); // 跨链转账都是系统合约交易
        tx.setInput(input);
        tx.setPub(pub);
        tx.setUseLocal(false);
        tx.setExtra("");
        tx.setSig(thkUtils.CreateSig(ecKeyPair, tx));
        Map result = web3.SendTx(tx);
        log("sendTx-result", result);
        final String hash = (String) result.get("TXhash");
        return ((Map) retryIfPending(
                () -> {
                    final Map txInfo = web3.GetTransactionByHash(tx.getChainId(), hash);
                    if (txInfo.get("errCode") == null) {
                        if (((int) txInfo.get("status")) == 1) {
                            log("sendTx-txInfo", txInfo);
                            return txInfo;
                        } else {
                            throw new RuntimeException("tx failed");
                        }
                    } else {
                        return null;
                    }
                }
        ));
    }
    
    public boolean isExpire(VccProof vccProof) {
        final int currentChainHeight = getChainHeight(vccProof.getToChain() + "");
        final int expireHeight = vccProof.getExpireHeight();
        log("isExpire-currentChainHeight-expireHeight", currentChainHeight + "-" + expireHeight);
        return currentChainHeight > expireHeight;
    }
    
    public int getChainHeight(String chainId) {
        final Map chainInfoMap = web3.GetStats(chainId);
        return (int) chainInfoMap.get("currentheight");
    }
    
    public static String getNonce(Transaction tx) {
        return getNonce(tx.getFromChainId(), tx.getFrom()) + "";
    }
    
    public static int getNonce(String chainId, String addr) {
        return web3.GetNonce(chainId, addr);
    }
    
    public Object retryIfPending(Callback callback) {
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 30 * 1000) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final Object result = callback.execute();
            if (result != null) {
                return result;
            }
            log("retry again");
        }
        throw new RuntimeException("timeout");
    }
    
    interface Callback {
        Object execute();
    }
}
