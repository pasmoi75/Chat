package messages.engine;

public class IncompleteMessageException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public IncompleteMessageException(int status,int total,int completed){
		System.out.println("Status :"+status+" total : "+total+" completed :"+completed);
		
	}
	
	public IncompleteMessageException(){
		super();
	}

}
