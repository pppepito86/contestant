package org.pesho.judge;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.pesho.grader.SubmissionScore;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Worker {

	private ObjectMapper mapper = new ObjectMapper();
	
	private String url;
	private boolean isFree = true;

	public Worker(String url) {
		if (!url.startsWith("http"))
			url = "http://" + url;
		if (url.endsWith("/"))
			url = url.substring(0, url.length() - 1);
		this.url = url;
	}

	public SubmissionScore grade(Map<String, Object> problem, Map<String, Object> submission, File file)
			throws Exception {
		int problemId = (int) problem.get("id");

		String submissionId = submission.get("id") + "_" + problem.get("name") + "_" + new Random().nextInt(100);

        HttpEntity entity = MultipartEntityBuilder.create()
                .addBinaryBody("file", file, ContentType.TEXT_PLAIN, file.getName())
                .addTextBody("metadata", "{\"problemId\":" + problemId + "}", ContentType.APPLICATION_JSON)
                .build();
		
		HttpPost post = new HttpPost(url + "/api/v1/submissions/" + submissionId);
		post.setEntity(entity);

		CloseableHttpClient httpclient = HttpClients.createDefault();
		httpclient.execute(post);
		httpclient.close();
		
		for (int i = 0; i < 600; i++) {
			if (isRunning(submissionId)) Thread.sleep(1000);
			else return getScore(submissionId);
		}
		throw new IllegalStateException("time out");
	}

	private boolean isRunning(String submissionId) throws IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet get = new HttpGet(url + "/api/v1/submissions/" + submissionId + "/status");
		CloseableHttpResponse response = httpclient.execute(get);
		String responseString = EntityUtils.toString(response.getEntity());
		httpclient.close();
		return "running".equals(responseString);
	}
	
	private SubmissionScore getScore(String submissionId) throws IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet get = new HttpGet(url + "/api/v1/submissions/" + submissionId + "/score");
		CloseableHttpResponse response = httpclient.execute(get);
		String responseString = EntityUtils.toString(response.getEntity());
		httpclient.close();
		SubmissionScore score = mapper.readValue(responseString, SubmissionScore.class);
		
		System.out.println("Response for " + submissionId + " is: " + response.getStatusLine() + ", points are: " + score.getScore());
		return score;
	}
	
	public boolean isAlive() {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(1000)
				  .setConnectionRequestTimeout(1000)
				  .setSocketTimeout(1000).build();
		try (CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build()) {
			HttpGet httpGet = new HttpGet(url + "/api/v1/health-check");
			CloseableHttpResponse response = httpclient.execute(httpGet);
			return response.getStatusLine().getStatusCode() == HttpStatus.OK.value();
		} catch (Exception e) {
			System.out.println("Cannot connect to worker " + url);
			return false;
		}
	}

	public void setFree(boolean isFree) {
		this.isFree = isFree;
	}

	public boolean isFree() {
		return isFree;
	}

	public String getUrl() {
		return url;
	}

}
