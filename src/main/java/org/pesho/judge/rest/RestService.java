package org.pesho.judge.rest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.task.TaskDetails;
import org.pesho.grader.task.TaskParser;
import org.pesho.judge.repositories.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RestService {

	public static final int CONTEST_ID = 1;
	public static final String CONTEST_NAME = "J";
	public static final int PROBLEM_NUMBER = 1;
	
	@Value("${work.dir}")
	private String workDir;
	
	@Autowired
	protected Repository repository;

    @PostMapping("/register")
    public ResponseEntity<?>  register(@RequestParam("username") String username,
                                       @RequestParam("password") String password,
                                       @RequestParam("password2") String password2,
                                       @RequestParam("email") String email,
                                       @RequestParam("name") String name) {
        username = username.toLowerCase();

        if (repository.getUserDetails(username).isPresent()) {
            return getResponse(ResponseMessage.getErrorMessage("Username is already taken"));
        }
        if (!password.equals(password2)) {
            return getResponse(ResponseMessage.getErrorMessage("Passwords does not match"));
        }

        repository.addUser(username, password, email, name);
        return getResponse(ResponseMessage.getOKMessage(""));
    }

	@PostMapping("/submit")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public ResponseEntity<?> submitFile(@RequestPart("file") MultipartFile file,
										@RequestParam("username") String username,
										@RequestParam("password") String password) {
        username = username.toLowerCase();

		if (!repository.getUser(username, password).isPresent()) {
			return getResponse(ResponseMessage.getErrorMessage("Username/password is incorrect"));
		}

		if (file == null || file.isEmpty()) {
			return getResponse(ResponseMessage.getErrorMessage("There is no file submitted"));
		}

		if (!FileType.isAllowed(file.getOriginalFilename())) {
			return getResponse(ResponseMessage.getErrorMessage("Program language not supported. Use java or c++ for your solution"));
		}

		if (file.getSize() > 64 * 1024) {
			return getResponse(ResponseMessage.getErrorMessage("File size exceeds 64KB"));
		}

		return getResponse(addSubmission(username, file));
	}

	public ResponseMessage addSubmission(String username, MultipartFile file) {
		long submissionTime = System.currentTimeMillis();

		String city = "Sofia";
		String problemName = repository
				.getProblem(CONTEST_ID, PROBLEM_NUMBER).get().get("name").toString();

		String fileName = file.getOriginalFilename();

		List<Map<String,Object>> submissions = repository.listSubmissions(username, 1);
		if (!submissions.isEmpty()) {
			Timestamp lastSubmissionTime = (Timestamp) submissions.get(0).get("upload_time");
			if (lastSubmissionTime.getTime() + 5 * 60 * 1000 > submissionTime) {
				return ResponseMessage.getErrorMessage("You are allowed to submit once in 5 minutes");
			}
		}

		Map<String, Object> contestMap = repository.getContest(CONTEST_ID).get();
		Timestamp endTime = (Timestamp) contestMap.get("end_time");
		if (submissionTime > endTime.getTime()) {
			return ResponseMessage.getErrorMessage("Contest is over");
		}

		int submissionId = repository.addSubmission(city, username, CONTEST_NAME, problemName, fileName);
		if (submissionId == 0) {
			return ResponseMessage.getErrorMessage("Problem occurred");
		}

		File sourceFile = getFile("submissions", String.valueOf(submissionId), fileName);
		try {
			FileUtils.copyInputStreamToFile(file.getInputStream(), sourceFile);
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseMessage.getErrorMessage("Problem occurred");
		}
		int submissionNumber = repository.userSubmissionsNumberForProblem(username, PROBLEM_NUMBER, submissionId);

		return ResponseMessage.getOKMessage("Submission uploaded with id " + submissionNumber);
	}

	@PostMapping("/submissions")
	public ResponseEntity<?>  listSubmissions(@RequestParam("username") String username,
											  @RequestParam("password") String password) {
        username = username.toLowerCase();

		if (!repository.getUser(username, password).isPresent()) {
			return getResponse(ResponseMessage.getErrorMessage("Username/password is incorrect"));
		}

		List<Map<String, Object>> submissions = taskSubmissions(username);
		return getResponse(ResponseMessage.getOKMessage(submissions));
	}

	public ResponseEntity<?> getResponse(ResponseMessage responseMessage) {
		return ResponseEntity.ok(responseMessage);
	}

	private TaskDetails getTaskDetails(String contestId, String problemNumber) {
    	File problemDir = Paths.get(workDir, "problem", contestId, problemNumber).toFile();
		TaskParser parser = new TaskParser(problemDir);
		TaskDetails details = TaskDetails.create(parser);
		return details;
    }
	
	protected String fixVerdict(String verdict, TreeSet<Integer> feedback) {
		if (feedback.size() == 0) return verdict;
		
		String[] split = verdict.split(",");
		if (split.length < feedback.last()) return verdict;
		
		for (int i = 1; i <= split.length; i++) {
			if (!feedback.contains(i)) split[i-1] = "?";
		}
		return String.join(",", split);
	}
	
	protected TreeSet<Integer> feedback(String feedback) {
		TreeSet<Integer> set = new TreeSet<>();
		if (feedback.trim().equalsIgnoreCase("full")) return set;
		
		String[] split = feedback.split(",");
		for (String s: split) set.add(Integer.valueOf(s.trim()));
		return set;
	}

	protected File getFile(String type, String city, String fileName) {
		String path = new File(workDir).getAbsolutePath() + File.separator + type + File.separator + city
				+ File.separator + fileName;
		return new File(path).getAbsoluteFile();
	}

	private List<Map<String, Object>> taskSubmissions(String username) {
		List<Map<String,Object>> submissions = repository.listSubmissions(username, PROBLEM_NUMBER);
    	for (int i = 0; i < submissions.size(); i++) {
    		submissions.get(i).put("number", submissions.size()-i);
    	}
    	String contestId = repository.getContestId(username).orElse(null);
    	TaskDetails details = getTaskDetails(contestId, String.valueOf(PROBLEM_NUMBER));
    	submissions.forEach(submission -> {
    		TreeSet<Integer> feedback = feedback(details.getFeedback());
			submission.put("verdict", fixVerdict(submission.get("verdict").toString(), feedback));
			if (feedback.size() != 0) submission.put("points", "?");
    	});
		return submissions;
	}


}
