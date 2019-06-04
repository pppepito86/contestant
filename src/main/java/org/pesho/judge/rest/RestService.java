package org.pesho.judge.rest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.pesho.grader.SubmissionScore;
import org.pesho.grader.step.StepResult;
import org.pesho.grader.step.Verdict;
import org.pesho.grader.task.TaskDetails;
import org.pesho.grader.task.TaskParser;
import org.pesho.judge.repositories.Repository;
import org.pesho.sandbox.SandboxExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class RestService {

	public static final int CONTEST_ID = 1;
	public static final String CONTEST_NAME = "J";
	public static final int PROBLEM_NUMBER = 1;
	
	@Value("${work.dir}")
	private String workDir;
	
	@Autowired
	protected Repository repository;

    protected ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/standings")
    public ResponseEntity<?>  standings() {
        List<Map<String, Object>> users = repository.listUsers();
        List<Map<String, Object>> submissions = repository.listSubmissions();

        HashMap<String, Integer> usersNumber = new HashMap<>();
        for (int i = 0; i < users.size(); i++) {
            usersNumber.put(users.get(i).get("name").toString(), i);
        }

        for (Map<String, Object> submission: submissions) {
            String username = submission.get("username").toString();

            if (submission.get("points") == null) continue;

            int userIndex = usersNumber.get(username);
            users.get(userIndex).put("id", submission.get("id"));
            users.get(userIndex).put("points", submission.get("points"));
            users.get(userIndex).put("upload_time", submission.get("upload_time"));
        }

        Iterator<Map<String, Object>> it = users.iterator();
        while (it.hasNext()) {
            if (!it.next().containsKey("points")) it.remove();
        }

        Collections.sort(users, (u1, u2) -> {
            int p1 = (int) u1.get("points");
            int p2 = (int) u2.get("points");
            if (p1 != p2) return p2-p1;

            int id1 = (int) u1.get("id");
            int id2 = (int) u2.get("id");
            return id1 - id2;
        });

        return getResponse(ResponseMessage.getOKMessage(users));
    }

    @PostMapping("/register")
    public ResponseEntity<?>  register(@RequestParam("username") String username,
                                       @RequestParam("password") String password,
                                       @RequestParam("password2") String password2,
                                       @RequestParam("name") String name,
                                       @RequestParam("email") String email,
                                       @RequestParam("linkedin") String linkedin,
                                       @RequestParam("privacy") Integer privacy,
                                       @RequestParam("contact") Optional<Boolean> checkbox) {
        if (privacy != 1 && privacy != 24) return ResponseEntity.badRequest().build();

        username = username.toLowerCase();
        if (username.length() < 6) return getResponse(ResponseMessage.getErrorMessage("Username should be at least 6 characters"));
        if (username.length() > 20) return getResponse(ResponseMessage.getErrorMessage("Username should be at most 20 characters"));
        if (!username.matches("^[a-z0-9]{6,20}$")) return getResponse(ResponseMessage.getErrorMessage("Username should contain latin letters and numbers only"));
        if (repository.getUserDetails(username).isPresent()) return getResponse(ResponseMessage.getErrorMessage("Username is already taken"));

        if (!password.equals(password2)) return getResponse(ResponseMessage.getErrorMessage("Passwords does not match"));

        if (password.length() < 6) return getResponse(ResponseMessage.getErrorMessage("Password too short"));
        if (password.length() > 100) return getResponse(ResponseMessage.getErrorMessage("Password too long"));

        name = name.trim();
        email = email.trim();
        if (name.isEmpty()) return getResponse(ResponseMessage.getErrorMessage("Name should be provided"));
        if (email.isEmpty()) return getResponse(ResponseMessage.getErrorMessage("Email should be provided"));

        repository.addUser(username, password, email, name, linkedin, checkbox.orElse(false).toString() + ", " + privacy);
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
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseMessage.getErrorMessage("Problem occurred");
		}

        if (FileType.isJava(fileName)) {
            removePackageDeclaration(sourceFile);
        }

        int submissionNumber = repository.userSubmissionsNumberForProblem(username, PROBLEM_NUMBER, submissionId);

		return ResponseMessage.getOKMessage("Submission uploaded with id " + submissionNumber);
	}

    private void removePackageDeclaration(File sourceFile) {
        try {
            new ProcessExecutor().command("sed", "-i", "1 s/^package.*//", sourceFile.getAbsolutePath()).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    	TaskDetails taskDetails = getTaskDetails(String.valueOf(CONTEST_ID), String.valueOf(PROBLEM_NUMBER));
    	submissions.forEach(submission -> {
    		TreeSet<Integer> feedback = feedback(taskDetails.getFeedback());
			submission.put("verdict", fixVerdict(submission.get("verdict").toString(), feedback));
            String reason = null;
			if ("CE".equals(submission.get("verdict"))) {
                reason = "";
                try {
                    String submissionDetails = submission.get("details").toString();
                    if (submissionDetails != null && !submissionDetails.isEmpty()) {
                        SubmissionScore score = mapper.readValue(submissionDetails, SubmissionScore.class);
                        if (score.getScoreSteps().containsKey("Compile")) reason = score.getScoreSteps().get("Compile").getReason();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            submission.put("reason", reason);

			if (feedback.size() != 0) submission.put("points", "?");

			try {
			    if (submission.get("points") != null) {
                    String[] split = submission.get("verdict").toString().split(",");
                    String submissionDetails = submission.get("details").toString();
                    SubmissionScore score = mapper.readValue(submissionDetails, SubmissionScore.class);
                    for (int i = 1; i < score.getScoreSteps().size(); i++) {
                        StepResult step = score.getScoreSteps().get("Test" + i);
                        if (step.getVerdict() == Verdict.TL) split[i-1]+="/-";
                        else split[i-1] += "/" + String.format("%.1fs", step.getTime());
                    }
                    submission.put("verdict", String.join( ",", split));
                } else submission.remove("verdict");
            } catch (Exception e) {
			    e.printStackTrace();
            }

    	});
		return submissions;
	}

}
