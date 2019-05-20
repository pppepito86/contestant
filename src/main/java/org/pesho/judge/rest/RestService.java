package org.pesho.judge.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
                                       @RequestParam("password") String passwordRepeated,
                                       @RequestParam("email") String email) {
        username = username.toLowerCase();

        if (repository.getUserDetails(username).isPresent()) {
            return getResponse(ResponseMessage.getErrorMessage("Username is already taken"));
        }
        if (!password.equals(passwordRepeated)) {
            return getResponse(ResponseMessage.getErrorMessage("Passwords does not match"));
        }

        repository.addUser(username, password, email);
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


//	@RequestMapping("/me")
//	public ResponseEntity<?> me() {
//		return repository.getUserDetails(getUsername())
//				.map(ResponseEntity::ok)
//				.orElse(ResponseEntity.notFound().build());
//	}

//	//TODO check contest is started
//	@RequestMapping("/tasks")
//	public ResponseEntity<?> tasks() {
//		Optional<String> contest = getContest();
//		return contest.map(c -> repository.listContestTasks(c))
//				.map(ResponseEntity::ok)
//				.orElse(ResponseEntity.notFound().build());
//	}
	
//	@RequestMapping("/tasks/{problemNumber}")
//	public ResponseEntity<?> taskDetails(@PathVariable int problemNumber) {
//		return repository.getContestTask(getUsername(), problemNumber).map(task -> {
//			TaskDetails details = getTaskDetails(task.get("contestId").toString(), task.get("number").toString());
//			task.put("time", details.getTime());
//			task.put("memory", details.getMemory());
//			return task;
//		}).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
//	}

//    @GetMapping("/tasks/{taskNumber}/pdf")
//    public ResponseEntity<?> downloadPdf(@PathVariable("taskNumber") int number,
//			@RequestParam(value = "download", defaultValue = "false") boolean download) throws Exception {
//
//    	String contestId = repository.getContestId(getUsername()).get();
//	    return downloadPdf(Integer.valueOf(contestId), number, download);
//    }
    
    public ResponseEntity<?> downloadPdf(int contestId, int number, boolean download) throws Exception {
    	HttpHeaders respHeaders = new HttpHeaders();
    	if (download) {
    		respHeaders.setContentDispositionFormData("attachment", "problem" + number + ".pdf");
    	} else {
    		respHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
    	}
	    
	    File problemDir = getFile("problem", String.valueOf(contestId), String.valueOf(number));
	    File pdf = findPdf(problemDir);
	    if (pdf == null) return ResponseEntity.noContent().build();
	    
	    InputStream is = new FileInputStream(pdf);
		InputStreamResource inputStreamResource = new InputStreamResource(is);
	    
		return new ResponseEntity<InputStreamResource>(inputStreamResource, 
	    		respHeaders, HttpStatus.OK);
    }

	protected File findPdf(File problemDir) {
		File[] files = problemDir.listFiles();
		if (files == null) return null;
		
		for (File file: files) {
			if (file.isFile() && file.getName().endsWith(".pdf")) {
				return file;
			}
			if (file.isDirectory()) {
				File pdf = findPdf(file);
				if (pdf != null) return pdf;
			}
		}
		return null;
	}
    
//	@RequestMapping("/tasks/{problemNumber}/solutions")
//	public ResponseEntity<?> taskSolutions(
//			@PathVariable int problemNumber) {
//		List<Map<String, Object>> submissions = taskSubmissions(problemNumber);
//		return ResponseEntity.ok(submissions);
//	}
//
//    @RequestMapping("/tasks/{problemNumber}/solutions/{solutionNumber}")
//    public ResponseEntity<?> taskSolution(
//    		@PathVariable int problemNumber,
//    		@PathVariable int solutionNumber) throws Exception {
//    	List<Map<String,Object>> submissions = taskSubmissions(problemNumber);
//    	Map<String, Object> submission = submissions.get(submissions.size()-solutionNumber);
//
//		List<List<Integer>> groups = groups(Integer.valueOf(submission.get("problem_id").toString()));
//		submission.put("groups", groups);
//
//    	TreeSet<Integer> feedback = feedback(Integer.valueOf(submission.get("problem_id").toString()));
//		submission.put("verdict", fixVerdict(submission.get("verdict").toString(), feedback));
//
//    	String details = submission.get("details").toString();
//		if (details != null && !details.isEmpty()) {
//			SubmissionScore score = mapper.readValue(details, SubmissionScore.class);
//			for(String key: score.getScoreSteps().keySet()) {
//				StepResult stepResult = score.getScoreSteps().get(key);
//				if (!key.startsWith("Test")) continue;
//
//				int testId = Integer.valueOf(key.split("Test")[1]);
//				if (feedback.size() == 0 || feedback.contains(testId)) continue;
//
//				stepResult.setExpectedOutput("");
//				stepResult.setOutput("");
//				stepResult.setReason("");
//				stepResult.setTime(null);
//				stepResult.setVerdict(Verdict.HIDDEN);
//			}
//
//			if (feedback.size() == 0) {
//				submission.put("score", Math.round(score.getScore()));
//			} else {
//				submission.put("score", "?");
//			}
//			submission.put("compile", score.getScoreSteps().get("Compile"));
//			score.getScoreSteps().remove("Compile");
//
//			LinkedHashMap<String,StepResult> scoreSteps = score.getScoreSteps();
//			ArrayList<HashMap<String, Object>> newScoreSteps = new ArrayList<>();
//			for (Map.Entry<String, StepResult> entry: scoreSteps.entrySet()) {
//				HashMap<String, Object> m = new HashMap<>();
//				m.put("name", entry.getKey());
//				m.put("verdict", entry.getValue().getVerdict());
//				m.put("reason", entry.getValue().getReason());
//				m.put("time", entry.getValue().getTime());
//				newScoreSteps.add(m);
//			}
//
//			submission.put("tests", newScoreSteps);
//
//			LinkedHashMap<String, StepResult> steps = new LinkedHashMap<>();
//			if (groups.size() <= score.getScoreSteps().size()) {
//				for (int i = 0; i < groups.size(); i++) {
//					StepResult step = new StepResult();
//					boolean hidden = false;
//					boolean ok = true;
//					for (int t: groups.get(i)) {
//						Verdict v = score.getScoreSteps().get("Test"+t).getVerdict();
//						if (v == Verdict.HIDDEN) hidden = true;
//						if (v != Verdict.OK) ok = false;
//					}
//					if (hidden) step.setVerdict(Verdict.HIDDEN);
//					else if (ok) step.setVerdict(Verdict.OK);
//					steps.put("Group"+(i+1), step);
//
//					for (int j = 0; j < groups.get(i).size(); j++) {
//						steps.put("Test"+groups.get(i).get(j), score.getScoreSteps().get("Test"+groups.get(i).get(j)));
//					}
//				}
//			}
//			submission.put("steps", steps);
//		}
//
//		File sourceFile = getFile("submissions", String.valueOf(submission.get("id")), submission.get("file").toString());
//		String source = FileUtils.readFileToString(sourceFile, Charset.forName("UTF-8"));
//		submission.put("source", source);
//
//		return ResponseEntity.ok(submission);
//    }
	
	private TaskDetails getTaskDetails(String contestId, String problemNumber) {
    	File problemDir = Paths.get(workDir, "problem", contestId, problemNumber).toFile();
		TaskParser parser = new TaskParser(problemDir);
		TaskDetails details = TaskDetails.create(parser);
		return details;
    }
	
//	private Optional<String> getContest() {
//		return repository.getUserDetails(getUsername())
//				.map(user -> user.get("contest"))
//				.map(Object::toString);
//	}
    
	protected String fixVerdict(String verdict, TreeSet<Integer> feedback) {
		if (feedback.size() == 0) return verdict;
		
		String[] split = verdict.split(",");
		if (split.length < feedback.last()) return verdict;
		
		for (int i = 1; i <= split.length; i++) {
			if (!feedback.contains(i)) split[i-1] = "?";
		}
		return String.join(",", split);
	}
	
	private List<List<Integer>> groups(int problemId) throws Exception {
		Map<String, Object> problem = repository.getProblem(problemId).get();
		File problemDir = getFile("problem", problem.get("contest_id").toString(), problem.get("number").toString());

		TaskParser parser = new TaskParser(problemDir);
		TaskDetails details = TaskDetails.create(parser);
		String groups = details.getGroups();
		
		List<List<Integer>> result = new ArrayList<>();
		if (groups.isEmpty()) return result;
		
		String[] split = groups.split(",");
		for (int i = 0; i < split.length; i++) {
			String[] split2 = split[i].split("-");
			int start = Integer.valueOf(split2[0]);
			int end = Integer.valueOf(split2[1]);

			List<Integer> list = new ArrayList<>();
			for (int j = start; j <= end; j++) list.add(j);
			
			result.add(list);
		}
		
		return result;
	}
	
	private TreeSet<Integer> feedback(int problemId) throws Exception {
		Map<String, Object> problem = repository.getProblem(problemId).get();
		File problemDir = getFile("problem", problem.get("contest_id").toString(), problem.get("number").toString());

		TaskParser parser = new TaskParser(problemDir);
		TaskDetails details = TaskDetails.create(parser);
		return feedback(details.getFeedback());
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
	
	protected File getFile(String type, String city, String group, String fileName) {
		String path = new File(workDir).getAbsolutePath() + File.separator + type + File.separator + city
				+ File.separator + group + File.separator + fileName;
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

//	@RequestMapping("/time")
//	public ResponseEntity<?> timeLeft() {
//		return ResponseEntity.ok(getTimes());
//	}
	
//	private HashMap<String, Object> getTimes() {
//		long currentTime = System.currentTimeMillis();
//		Timestamp startTime = (Timestamp) repository.getContest(getUsername()).get().get("start_time");
//		Timestamp endTime = (Timestamp) repository.getContest(getUsername()).get().get("end_time");
//		long timeTillStart = startTime.getTime() - currentTime;
//		long timeTillEnd = endTime.getTime() - currentTime;
//
//		HashMap<String, Object> map = new HashMap<>();
//		map.put("timeTillStart", timeTillStart);
//		map.put("timeTillEnd", timeTillEnd);
//
//		return map;
//	}
//
    
}
