package redempt.redmaven;

public record ProjectInfo (String pkg, String name, String version) {
	
	public String getProjectName() {
		return pkg + ":" + name;
	}
	
	public String getFolderStructure() {
		return pkg.replace(".", "/") + "/" + name + "/" + version;
	}
	
}
