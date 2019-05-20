package org.pesho.judge.repositories;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class Repository {

    @Autowired
    private JdbcTemplate template;

    public Optional<Map<String, Object>> getUserDetails(String username) {
    	return template.queryForList(
    			"SELECT name, contest, display_name, grade, school, city FROM users WHERE name=?", username)
    			.stream().findAny();
    }

	public Optional<Map<String, Object>> getUser(String username, String password) {
		return template.queryForList(
				"SELECT name FROM users WHERE name=? and password=?", username, password)
				.stream().findAny();
	}

    public Optional<Map<String, Object>> getContest(String username) {
    	return template.queryForList(
    			"SELECT c.id, c.start_time, c.end_time FROM contests as c" +
    			" INNER JOIN users AS u on u.contest=c.name" +
    			" WHERE u.name=?", username)
    			.stream().findAny();
    }

    
    public Optional<String> getContestId(String username) {
    	return template.queryForList(
    			"SELECT c.id FROM contests as c" +
    			" INNER JOIN users AS u on u.contest=c.name" +
    			" WHERE u.name=?", username)
    			.stream().map(c -> c.get("id")).map(Object::toString).findAny();
    }

	public Optional<Map<String, Object>> getContestTask(String userName, int problemNumber) {
		return template.queryForList(
				"SELECT p.name, p.number, c.id as contestId FROM problems as p" +
				" INNER JOIN contests AS c ON c.id=p.contest_id" +
				" INNER JOIN users AS u ON u.contest=c.name" +
				" WHERE u.name=? AND p.number=?", userName, problemNumber)
				.stream().findAny();
	}
    
	public List<Map<String, Object>> listContestTasks(String contestName) {
		return template.queryForList(
				"SELECT p.name, p.number FROM problems as p" +
				" INNER JOIN contests AS c ON c.id=p.contest_id" +
				" WHERE c.name=? ORDER BY p.number ASC", contestName);
	}

	public List<Map<String, Object>> listSubmissions(String username, int problemNumber) {
		return template.queryForList(
				"SELECT s.id,s.file,s.verdict,s.details,s.points,s.upload_time,p.name,p.id as problem_id,u.contest FROM submissions AS s" +
				" INNER JOIN users AS u ON u.name=s.username" + 
				" INNER JOIN problems AS p ON p.id=s.problem_id" +
				" WHERE u.name=? and p.number=?" +
				" ORDER BY s.upload_time DESC", username, problemNumber);
	}
    
	
	
	
	
	
    public synchronized int addContest(String name, Timestamp start, Timestamp end) {
    	template.update("INSERT INTO contests(name, start_time, end_time) VALUES(?, ?, ?)", 
    			name, start, end);
    	
    	Optional<Object> last = template.queryForList("SELECT MAX(id) FROM contests").stream()
    			.map(x -> x.get("MAX(id)")).findFirst();
    	
    	return (int) last.get();
    }

    public synchronized int addProblem(String name, int contestId, int number, String file, String checksum) {
        template.update("INSERT INTO problems(name, number, contest_id, file, checksum) VALUES(?, ?, ?, ?, ?)",
        		name,
        		number,
        		contestId,
                file,
                checksum);
        
		Optional<Object> first = template.queryForList("SELECT MAX(id) FROM problems").stream()
				.map(x -> x.get("MAX(id)")).findFirst();
		
		return (int) first.get();
	}

	public void updateProblem(int id, String name, String file, String checksum) {
        template.update("UPDATE problems set name=?, file=?, checksum=? where id=?",
        		name, file, checksum, id);
	}
	
	public Optional<Map<String, Object>> getProblem(int id) {
		return template.queryForList(
				"select * from problems where id=?", 
				id).stream().findFirst();
	}
	
	public Optional<Map<String, Object>> getProblem(int contestId, int number) {
		return template.queryForList(
				"select * from problems where contest_id=? AND number=?", 
				contestId, number).stream().findFirst();
	}

	public Optional<Map<String, Object>> getProblem(String contestName, String problemName) {
		return template.queryForList(
				"select problems.id, problems.name from problems" +
				" inner join contests on contests.name=? and contests.id=problems.contest_id" +
				" where problems.name=?", 
				contestName, problemName).stream().findFirst();
	}
	
	public List<Map<String, Object>> getContestProblems(String contest) {
		return template.queryForList(
				"select * from problems where contest=?", contest);
	}
	
	public List<Map<String, Object>> listProblems() {
        return template.queryForList("SELECT * from problems");
	}

	public List<Map<String, Object>> listProblemsWithContest() {
        return template.queryForList("SELECT problems.id as id, problems.name as problem_name, contests.name as contest_name from problems inner join contests on contests.id=problems.contest_id");
	}
	
	public List<Map<String, Object>> listContestProblems(int contestId) {
		return template.queryForList("SELECT * from problems where contest_id=? order by number", contestId);
	}
	
	public List<Map<String, Object>> listContests() {
		return template.queryForList("SELECT * from contests");
	}
	
	public Optional<Map<String, Object>> getContest(int id) {
		return template.queryForList("SELECT * from contests where id=?", id).stream().findFirst();
	}

//	public Optional<Map<String, Object>> getContest(String contest) {
//		return template.queryForList("SELECT * from contests where name=?", contest).stream().findFirst();
//	}
	
	public List<Map<String, Object>> listSubmissions() {
		return template.queryForList("SELECT * from submissions");
	}
	
	public List<Map<String, Object>> listCitySubmissions(String city) {
		return template.queryForList("SELECT * from submissions where city=?", city);
	}

	public List<Map<String, Object>> lastSimilarSubmission(int problemId, String username, String city) {
		return template.queryForList("SELECT * from submissions where city=? and username=? and problem_id=? order by id DESC", 
				city, username, problemId);
	}
	
	public List<Map<String, Object>> listUserSubmissionsForProblem(String username, int problemNumber) {
		return template.queryForList("SELECT submissions.id,submissions.verdict,submissions.points,submissions.upload_time from submissions" +
				" inner join users on users.name=? and users.name=submissions.username" + 
				" inner join contests on contests.name=users.contest" + 
				" inner join problems on problems.number=? and problems.contest_id=contests.id and problems.id=submissions.problem_id" +
				" order by submissions.upload_time desc",
				username, problemNumber);
	}
	
	public int userSubmissionsNumberForProblem(String username, int problemNumber, int submissionId) {
		return template.queryForList("SELECT submissions.id from submissions" +
				" inner join users on users.name=? and users.name=submissions.username" + 
				" inner join contests on contests.name=users.contest" + 
				" inner join problems on problems.number=? and problems.contest_id=contests.id and problems.id=submissions.problem_id" +
				" where submissions.id <= ?" +
				" order by submissions.upload_time desc",
				username, problemNumber, submissionId).size();
	}
	
	public synchronized int addSubmission(String city, String username, String contest, String problemName, String file) {
		Optional<Map<String,Object>> problem = getProblem(contest, problemName);
		if (problem.isPresent()) {
			template.update("INSERT INTO submissions(city, username, file, verdict, details, problem_id) VALUES(?, ?, ?, ?, ?, ?)",
                city, username, file, "waiting", "", (int) problem.get().get("id"));
        
			Optional<Object> first = template.queryForList("SELECT MAX(id) FROM submissions").stream()
				.map(x -> x.get("MAX(id)")).findFirst();
			return (int) first.get();
		}
		return 0;
	}
	
    public synchronized void addLog(String topic, String title, String message) {
    	template.update("INSERT INTO logs(topic, title, message) VALUES(?, ?, ?)", 
    			topic, title, message);
    }

    public synchronized void addUser(String username, String password, String email, String displayName) {
        template.update("INSERT INTO users(role, name, password, display_name, contest, grade) VALUES(?, ?, ?, ?, ?, ?)",
                "USER", username, password, displayName, "J", email);
    }
    
    public synchronized void addIpLog(String username, String operation, String localIp, String publicIp) {
    	template.update("INSERT INTO ips(username, operation, local_ip, public_ip) VALUES(?, ?, ?, ?)", 
    			username, operation, localIp, publicIp);
    }
    
}
