package stest.tron.wallet.dailybuild.http;

import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI.Note;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Utils;
import org.tron.core.config.args.Args;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.HttpMethed;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.ShieldAddressInfo;
import stest.tron.wallet.common.client.utils.ShieldNoteInfo;

@Slf4j
public class HttpTestZenToken002 {

  private String httpnode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(0);
  private String httpSolidityNode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);
  private String foundationZenTokenKey = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.zenTokenOwnerKey");
  byte[] foundationZenTokenAddress = PublicMethed.getFinalAddress(foundationZenTokenKey);
  private String zenTokenId = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.zenTokenId");
  private Long zenTokenFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.zenTokenFee");

  Optional<ShieldAddressInfo> sendShieldAddressInfo;
  Optional<ShieldAddressInfo> receiverShieldAddressInfo;
  String sendShieldAddress;
  String receiverShieldAddress;
  List<Note> shieldOutList = new ArrayList<>();
  String memo1;
  String memo2;
  ShieldNoteInfo sendNote;
  ShieldNoteInfo receiveNote;
  ShieldNoteInfo noteByOvk;
  ShieldNoteInfo noteByIvk;
  String assetIssueId;

  private Long sendTokenAmount = 7 * zenTokenFee;
  private JSONObject responseContent;
  private HttpResponse response;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] zenTokenOwnerAddress = ecKey1.getAddress();
  String zenTokenOwnerKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(foundationZenTokenKey);
    PublicMethed.printAddress(zenTokenOwnerKey);
    response = HttpMethed
        .transferAsset(httpnode, foundationZenTokenAddress, zenTokenOwnerAddress, zenTokenId,
            sendTokenAmount, foundationZenTokenKey);
    org.junit.Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
    Args.getInstance().setFullNodeAllowShieldedTransaction(true);
    sendShieldAddressInfo = HttpMethed.generateShieldAddress(httpnode);
    sendShieldAddress = sendShieldAddressInfo.get().getAddress();
    logger.info("sendShieldAddress:" + sendShieldAddress);
    memo1 = "Shield memo1 in " + System.currentTimeMillis();
    shieldOutList = HttpMethed.addShieldOutputList(httpnode, shieldOutList, sendShieldAddress,
        "" + (sendTokenAmount - zenTokenFee), memo1);

    response = HttpMethed
        .sendShieldCoin(httpnode, zenTokenOwnerAddress, sendTokenAmount, null, null, shieldOutList,
            null, 0, zenTokenOwnerKey);
    org.junit.Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    shieldOutList.clear();
    HttpMethed.waitToProduceOneBlock(httpnode);
    sendNote = HttpMethed.scanNoteByIvk(httpnode, sendShieldAddressInfo.get());
  }

  @Test(enabled = true, description = "Shield to shield transaction by http")
  public void test01ShieldToShieldTransaction() {
    receiverShieldAddressInfo = HttpMethed.generateShieldAddress(httpnode);
    receiverShieldAddress = receiverShieldAddressInfo.get().getAddress();

    shieldOutList.clear();
    memo2 = "Send shield to receiver shield memo in" + System.currentTimeMillis();
    shieldOutList = HttpMethed.addShieldOutputList(httpnode, shieldOutList, receiverShieldAddress,
        "" + (sendNote.getValue() - zenTokenFee), memo2);

    response = HttpMethed
        .sendShieldCoin(httpnode, null, 0, sendShieldAddressInfo.get(), sendNote, shieldOutList,
            null, 0, null);
    org.junit.Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    HttpMethed.waitToProduceOneBlock(httpnode);
    Long afterAssetBalance = HttpMethed
        .getAssetIssueValue(httpnode, zenTokenOwnerAddress, assetIssueId);

    receiveNote = HttpMethed.scanNoteByIvk(httpnode, receiverShieldAddressInfo.get());

    Assert.assertTrue(receiveNote.getValue() == sendNote.getValue() - zenTokenFee);
    Assert.assertEquals(ByteArray.toHexString(memo2.getBytes()),
        ByteArray.toHexString(receiveNote.getMemo()));

    response = HttpMethed.getSpendResult(httpnode, sendShieldAddressInfo.get(), sendNote);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertEquals(responseContent.getString("result"), "true");
    Assert.assertEquals(responseContent.getString("message"), "input note already spent");
  }

  @Test(enabled = true, description = "Scan note by ivk and scan not by ivk on FullNode by http")
  public void test02ScanNoteByIvkAndOvk() {
    //Scan sender note by ovk equals scan receiver note by ivk on FullNode
    noteByOvk = HttpMethed.scanNoteByOvk(httpnode, sendShieldAddressInfo.get());
    noteByIvk = HttpMethed.scanNoteByIvk(httpnode, receiverShieldAddressInfo.get());
    Assert.assertEquals(noteByIvk.getValue(), noteByOvk.getValue());
    Assert.assertEquals(noteByIvk.getMemo(), noteByOvk.getMemo());
    Assert.assertEquals(noteByIvk.getR(), noteByOvk.getR());
    Assert.assertEquals(noteByIvk.getPaymentAddress(), noteByOvk.getPaymentAddress());
  }

  @Test(enabled = true, description = "Scan note by ivk and scan not by ivk on Solidity by http")
  public void test03ScanNoteByIvkAndOvkFromSolidity() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSolidityNode);
    //Scan sender note by ovk equals scan receiver note by ivk on Solidity
    noteByOvk = HttpMethed.scanNoteByOvkFromSolidity(httpSolidityNode, sendShieldAddressInfo.get());
    noteByIvk = HttpMethed
        .scanNoteByIvkFromSolidity(httpSolidityNode, receiverShieldAddressInfo.get());
    Assert.assertEquals(noteByIvk.getValue(), noteByOvk.getValue());
    Assert.assertEquals(noteByIvk.getMemo(), noteByOvk.getMemo());
    Assert.assertEquals(noteByIvk.getR(), noteByOvk.getR());
    Assert.assertEquals(noteByIvk.getPaymentAddress(), noteByOvk.getPaymentAddress());
  }

  @Test(enabled = true, description = "Shield to public transaction by http")
  public void test04ShieldToPublicTransaction() {
    response = HttpMethed.getAccount(httpnode, foundationZenTokenAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    assetIssueId = responseContent.getString("asset_issued_ID");
    logger.info("assetIssueId:" + assetIssueId);

    final Long beforeAssetBalance = HttpMethed
        .getAssetIssueValue(httpnode, zenTokenOwnerAddress, assetIssueId);
    response = HttpMethed.getAccountReource(httpnode, zenTokenOwnerAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    final Long beforeNetUsed = responseContent.getLong("freeNetUsed");

    shieldOutList.clear();
    response = HttpMethed
        .sendShieldCoin(httpnode, null, 0, receiverShieldAddressInfo.get(), receiveNote,
            shieldOutList,
            zenTokenOwnerAddress, receiveNote.getValue() - zenTokenFee, null);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    HttpMethed.waitToProduceOneBlock(httpnode);

    Long afterAssetBalance = HttpMethed
        .getAssetIssueValue(httpnode, zenTokenOwnerAddress, assetIssueId);
    response = HttpMethed.getAccountReource(httpnode, zenTokenOwnerAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    Long afterNetUsed = responseContent.getLong("freeNetUsed");

    logger.info("beforeAssetBalance:" + beforeAssetBalance);
    logger.info("afterAssetBalance:" + afterAssetBalance);
    Assert.assertTrue(
        afterAssetBalance - beforeAssetBalance == receiveNote.getValue() - zenTokenFee);
    Assert.assertTrue(beforeNetUsed == afterNetUsed);

    response = HttpMethed.getSpendResult(httpnode, receiverShieldAddressInfo.get(), receiveNote);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertEquals(responseContent.getString("result"), "true");
    Assert.assertEquals(responseContent.getString("message"), "input note already spent");
  }

  /**
   * constructor.
   */
  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    final Long assetBalance = HttpMethed
        .getAssetIssueValue(httpnode, zenTokenOwnerAddress, assetIssueId);
    HttpMethed
        .transferAsset(httpnode, zenTokenOwnerAddress, foundationZenTokenAddress, assetIssueId,
            assetBalance, zenTokenOwnerKey);
  }
}