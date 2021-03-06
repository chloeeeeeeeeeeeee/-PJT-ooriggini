package com.ssafy.bab.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.ssafy.bab.dao.ContributionDao;
import com.ssafy.bab.dao.ContributorDao;
import com.ssafy.bab.dao.ItemDao;
import com.ssafy.bab.dao.OrderDao;
import com.ssafy.bab.dao.PaymentDao;
import com.ssafy.bab.dao.StoreDao;
import com.ssafy.bab.dao.StoreVariablesDao;
import com.ssafy.bab.dao.UserDao;
import com.ssafy.bab.dto.Contribution;
import com.ssafy.bab.dto.Contributor;
import com.ssafy.bab.dto.Item;
import com.ssafy.bab.dto.KPaymentInfo;
import com.ssafy.bab.dto.KakaoPayApproval;
import com.ssafy.bab.dto.KakaoPayInfo;
import com.ssafy.bab.dto.KakaoPayReady;
import com.ssafy.bab.dto.KakaoPaySuccessData;
import com.ssafy.bab.dto.Orders;
import com.ssafy.bab.dto.Payment;
import com.ssafy.bab.dto.PaymentItem;
import com.ssafy.bab.dto.StoreVariables;
import com.ssafy.bab.dto.User;

import lombok.extern.java.Log;

@Service
@Log
public class KaKaoPayServiceImpl implements KakaoPayService {
	
	@Value("${Kakao.APP_ADMIN_KEY}")
	private String APP_ADMIN_KEY;
	
	@Value("${Kakao.KIOSK_RETURN_URL}")
	private String KIOSK_RETURN_URL;
	
	@Value("${Kakao.WEB_RETURN_URL}")
	private String WEB_RETURN_URL;
	
	@Autowired
	PaymentDao paymentDao;
	
	@Autowired
	OrderDao orderDao;
	
	@Autowired
	ContributionDao contributionDao;
	
	@Autowired
	ItemDao itemDao;
	
	@Autowired
	StoreDao storeDao;
	
	@Autowired
	UserDao userDao;
	
	@Autowired
	ContributorDao contributorDao;
	
	@Autowired
	StoreVariablesDao storeVariablesDao;
	
	private final String HOST = "https://kapi.kakao.com";
	private KakaoPayReady kakaoPayReady = new KakaoPayReady();
	private KakaoPayApproval kakaoPayApproval = new KakaoPayApproval();
	private RestTemplate restTemplate = new RestTemplate();
	private KakaoPayInfo kakaoPayInfo = new KakaoPayInfo();
	
	
	@Override
	public HttpHeaders headers() {
		HttpHeaders headers = new HttpHeaders(); 
		headers.add("Authorization", "KakaoAK " + APP_ADMIN_KEY); 
		headers.add("Accept", "application/x-www-form-urlencoded;charset=utf-8"); 
		headers.add("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";"+ "charset=UTF-8"); 
		return headers;
	}

	@Override
	public String kakaoPayReady(KPaymentInfo paymentInfo) {
		
		kakaoPayInfo.setPaymentInfo(paymentInfo);
		
		// ????????????
		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.HOUR, 9);
		String wdate = vans.format(cal.getTime());
		kakaoPayInfo.setPartner_order_id(wdate);
		
		// ????????? ??????
		String item_name = paymentInfo.getItemList().get(0).getItemName();
		if(paymentInfo.getItemList().size() > 1) {
			item_name += " ??? " + (paymentInfo.getTotalCount() - 1) + "???";
		}
		
		// itemCount = 0 ??? ?????? ????????????
		for(int i = 0; i < paymentInfo.getItemList().size(); i++) {
			if(paymentInfo.getItemList().get(i).getItemCount() <= 0) return null;
			if(itemDao.findByItemIdAndStoreId(paymentInfo.getItemList().get(i).getItemId(), paymentInfo.getItemList().get(i).getStoreId()) == null) return null;
		}

		// ???????????? ???????????? ??????????????? ???????????? ????????? ?????? user ?????? ??????
        if(paymentInfo.getIsKiosk() == 1 && paymentInfo.getContributorPhone() != null && !paymentInfo.getContributorPhone().equals("")) {
        	User user = userDao.findByUserPhone(paymentInfo.getContributorPhone());
        	if(user != null)
        		paymentInfo.setUserSeq(user.getUserSeq());
        }
		
        // ???????????? ??????
		kakaoPayInfo.setPartner_user_id(Integer.toString(paymentInfo.getUserSeq()));
		
		// ????????? ????????? Body 
		MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>(); 
		params.add("cid", paymentInfo.getCid());											// ????????? ???????????? (?????????)
		params.add("partner_order_id", kakaoPayInfo.getPartner_order_id());	// ???????????? 
		params.add("partner_user_id", Integer.toString(paymentInfo.getUserSeq()));			// ???????????? ( ????????? ????????? ????????? -1 )
		params.add("item_name", item_name); 												// ?????????
		params.add("quantity", Integer.toString(paymentInfo.getTotalCount())); 				// ?????? ??? ??????
		params.add("total_amount", Integer.toString(paymentInfo.getTotalAmount())); 		// ?????? ??? ??????
		params.add("tax_free_amount", "0"); 												// ?????? ????????? ??????
		
		if(paymentInfo.getIsKiosk() == 1) {
			params.add("approval_url", KIOSK_RETURN_URL + "/paymentCheck"); 				// ??????
//			params.add("approval_url", KIOSK_RETURN_URL + "/payment/kakaopaySuccess"); 
			params.add("cancel_url", KIOSK_RETURN_URL + "/payment/kakaopayCancel"); 		// ??????
			params.add("fail_url", KIOSK_RETURN_URL + "/payment/kakaopayFail"); 			// ??????
		}else {
			params.add("approval_url", WEB_RETURN_URL + "/paymentCheck"); 					// ??????
			params.add("cancel_url", WEB_RETURN_URL + "/payment/kakaopayCancel"); 			// ??????
			params.add("fail_url", WEB_RETURN_URL + "/payment/kakaopayFail"); 				// ??????
		}
		
		//test
//		params.add("approval_url", KIOSK_RETURN_URL + "/payment/kakaopaySuccess");		// ??????
//		params.add("cancel_url", KIOSK_RETURN_URL + "/payment/kakaopayCancel"); 		// ??????
//		params.add("fail_url", KIOSK_RETURN_URL + "/payment/kakaopayFail"); 			// ??????
		
		HttpEntity<MultiValueMap<String, String>> body = new HttpEntity<MultiValueMap<String, String>>(params, headers()); 
		try { 
			kakaoPayReady = restTemplate.postForObject(new URI(HOST + "/v1/payment/ready"), body, KakaoPayReady.class);
			log.info("" + kakaoPayReady); 
			//????????? 
			return kakaoPayReady.getNext_redirect_pc_url(); 
		} catch (RestClientException | URISyntaxException e) { 
			e.printStackTrace(); 
		} 

		//?????????
		return null;
	}

	@Override
	public KakaoPaySuccessData kakaoPayInfo(String pg_token) {
		 // ????????? ????????? Body

        MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>();
        params.add("cid", kakaoPayInfo.getPaymentInfo().getCid());
        params.add("tid", kakaoPayReady.getTid());
        params.add("partner_order_id", kakaoPayInfo.getPartner_order_id());
        params.add("partner_user_id", kakaoPayInfo.getPartner_user_id());
        params.add("pg_token", pg_token);
        params.add("total_amount", Integer.toString(kakaoPayInfo.getPaymentInfo().getTotalAmount()));
		
		HttpEntity<MultiValueMap<String, String>> body = new HttpEntity<MultiValueMap<String, String>>(params, headers());
        
        try {
            kakaoPayApproval = restTemplate.postForObject(new URI(HOST + "/v1/payment/approve"), body, KakaoPayApproval.class);
            log.info("" + kakaoPayApproval);
          
            // ?????????????????? ??????

            SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            Calendar cal = Calendar.getInstance();
    		cal.setTime(kakaoPayApproval.getApproved_at());
    		cal.add(Calendar.HOUR, -9);
            
            kakaoPayApproval.setApproved_at(cal.getTime());
            
            /*
             * ******* DB ????????? ???????????? *******
             */
            
            // payment ????????? ????????????
            Payment payment = new Payment();
            payment.setPaymentId(kakaoPayInfo.getPartner_order_id());
            payment.setPaymentAmount(kakaoPayInfo.getPaymentInfo().getTotalAmount());
            payment.setPaymentDate(kakaoPayApproval.getApproved_at());
            payment.setKakaopayCid(kakaoPayInfo.getPaymentInfo().getCid());
            payment.setKakaopayTid(kakaoPayReady.getTid());
            paymentDao.save(payment);
            
            // ????????????/??? ?????? ????????? ?????? user??? ?????? ?????? ????????? ????????? (??? ?????? ????????? null)
            User user = userDao.findByUserSeq(Integer.parseInt(kakaoPayInfo.getPartner_user_id()));

            // ????????????/??? ????????? ????????? ?????? contributor??? ?????? ????????? ????????? ????????? (??? ?????? ????????? null)
            Contributor contributor = null;
            if(user == null && kakaoPayInfo.getPaymentInfo().getContributorPhone() != null && !kakaoPayInfo.getPaymentInfo().getContributorPhone().equals("")) {
            	contributor = contributorDao.findByContributorPhone(kakaoPayInfo.getPaymentInfo().getContributorPhone());
            	
            	// ?????????????????? ???????????? ??? ???????????? ?????????????????? ?????? ?????? ??? contributor??? ?????? 
                if(contributor == null) {
                	contributor = new Contributor();
                	contributor.setContributorPhone(kakaoPayInfo.getPaymentInfo().getContributorPhone());
                	contributorDao.save(contributor);
                }
            }
            
            int totalSupportPrice = 0;
            int totalSupportItem = 0;
            
            // order, contribution ????????? ????????????
            for (PaymentItem paymentItem : kakaoPayInfo.getPaymentInfo().getItemList()) {
            	Item item = itemDao.findByItemIdAndStoreId(paymentItem.getItemId(), paymentItem.getStoreId());
				if(paymentItem.getSupport() == 1) {
					for(int i = 0; i < paymentItem.getItemCount(); i++) {
						// Contribution ????????? ????????????
						Contribution contribution = new Contribution();
						contribution.setItemId(paymentItem.getItemId());
						contribution.setStoreId(paymentItem.getStoreId());
						if(user != null) contribution.setUser(user);
						else if(contributor != null) contribution.setContributor(contributor);
						contribution.setContributionDate(kakaoPayApproval.getApproved_at());
						contribution.setContributionUse(0);
						contribution.setPayment(payment);
						contribution.setContributionMessage(paymentItem.getMsg());
						contributionDao.save(contribution);
					
					}
					// item ????????? ????????????
					item.setItemAvailable(item.getItemAvailable() + paymentItem.getItemCount());
					item.setItemTotal(item.getItemTotal() + paymentItem.getItemCount());
					itemDao.save(item);
					
					// for storeVariables, user ????????? ????????????  
					totalSupportPrice += item.getSupportPrice() * paymentItem.getItemCount();
					totalSupportItem += paymentItem.getItemCount();
				}else {
					Orders order = new Orders();
					order.setItemId(paymentItem.getItemId());
					order.setStoreId(paymentItem.getStoreId());
					order.setOrderDate(kakaoPayApproval.getApproved_at());
					order.setOrderCount(paymentItem.getItemCount());
					order.setPayment(payment);
					
//					!!!!!!!!!!!!orderDone ????????????!!!!!!!!!!!!!!!!
//					order.setOrderDone(kakaoPayApproval.getApproved_at());
					orderDao.save(order);
				}
			}
            
            // storeVariables, user ????????? ????????????
            if(totalSupportItem != 0) {
            	StoreVariables storeVariables = storeVariablesDao.findByStoreId(kakaoPayInfo.getPaymentInfo().getItemList().get(0).getStoreId());
            	storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() + totalSupportItem);
            	storeVariables.setStoreItemTotal(storeVariables.getStoreItemTotal() + totalSupportItem);
            	storeVariables.setStoreTotalContributionAmount(storeVariables.getStoreTotalContributionAmount() + totalSupportPrice);
            	storeVariablesDao.save(storeVariables);
            	
            	if(user != null) {
            		user.setUserTotalContributionAmount(user.getUserTotalContributionAmount() + totalSupportPrice);
            		user.setUserTotalContributionCount(user.getUserTotalContributionCount() + totalSupportItem);
            		userDao.save(user);
            	}
            }
      
            KakaoPaySuccessData result = new KakaoPaySuccessData();
            result.setPaymentId(payment.getPaymentId());
            result.setKakaoPayApproval(kakaoPayApproval);
            
            return result;
        
            
        } catch (Exception e) {
            // TODO Auto-generated catch block
        	System.out.println("???????????? ????????? ????????????.");
            e.printStackTrace();
        }
        
        return null;
	}



}
