package models.vo;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import thkContract.TypeEncoder;

import java.math.BigInteger;

public class VccProof {
    private int chainId;   // 所在链id，生成支票时无意义，兑现支票时传fromChain，撤销支票时传toChain
    private int fromChain;   // 转出链
    private String fromAddress;   // 转出账户
    private int nonce;      // 转出账户提交请求时的nonce
    private int toChain;   // 目标链
    private String toAddress;     // 目标账户
    private int expireHeight;// 过期高度，指的是当目标链高度超过（不含）这个值时，这张支票不能被支取，只能退回
    private BigInteger amount; // 金额
    
    public int getChainId() {
        return chainId;
    }
    
    public void setChainId(int chainId) {
        this.chainId = chainId;
    }
    
    public int getFromChain() {
        return fromChain;
    }
    
    public void setFromChain(int fromChain) {
        this.fromChain = fromChain;
    }
    
    public String getFromAddress() {
        return fromAddress;
    }
    
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
    
    public int getNonce() {
        return nonce;
    }
    
    public void setNonce(int nonce) {
        this.nonce = nonce;
    }
    
    public int getToChain() {
        return toChain;
    }
    
    public void setToChain(int toChain) {
        this.toChain = toChain;
    }
    
    public String getToAddress() {
        return toAddress;
    }
    
    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }
    
    public int getExpireHeight() {
        return expireHeight;
    }
    
    public void setExpireHeight(int expireHeight) {
        this.expireHeight = expireHeight;
    }
    
    public BigInteger getAmount() {
        return amount;
    }
    
    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }
    
    // 4字节FromChain + 20字节FromAddress + 8字节Nonce + 4字节ToChain + 20字节ToAddress +
// 8字节ExpireHeight + 1字节len(Amount.Bytes()) + Amount.Bytes()
// 均为BigEndian
    //0x000000022c7536e3605d9c16a7a3d7b1898e529396a65c23000000000000003c000000032c7536e3605d9c16a7a3d7b1898e529396a65c2400000000000083ec200000000000000000000000000000000000000000000000000000000000000001
    public String encode() {
        final StringBuilder sb = new StringBuilder("0x");
        String fromChainHex = getIntHex(fromChain, 4);
        sb.append(fromChainHex);
        String fromAddressHex = getAddressHex(fromAddress);
        sb.append(fromAddressHex);
        String nonceHex = getIntHex(nonce, 8);
        sb.append(nonceHex);
        String toChainHex = getIntHex(toChain, 4);
        
        sb.append(toChainHex);
        String toAddressHex = getAddressHex(toAddress);
        sb.append(toAddressHex);
        String toExpireHeight = getIntHex(expireHeight, 8);
        sb.append(toExpireHeight);
        sb.append("20");
        String amountsHex = TypeEncoder.encode(new Uint256(amount));
        sb.append(amountsHex);
        
        return sb.toString();
    }
    
    public static String getAddressHex(String address) {
        return TypeEncoder.encode(new Address(address)).substring(64 - 20 * 2);
    }
    
    public static String getIntHex(BigInteger num, int length) {
        return TypeEncoder.encode(new Uint256(num)).substring(64 - length * 2);
    }
    
    public static String getIntHex(long num, int length) {
        return TypeEncoder.encode(new Uint256(num)).substring(64 - length * 2);
    }
    
    @Override
    public String toString() {
        return "VccProof{" +
                "fromChain=" + fromChain +
                ", fromAddress='" + fromAddress + '\'' +
                ", nonce=" + nonce +
                ", toChain=" + toChain +
                ", toAddress='" + toAddress + '\'' +
                ", expireHeight=" + expireHeight +
                ", amount=" + amount +
                '}';
    }
}
    