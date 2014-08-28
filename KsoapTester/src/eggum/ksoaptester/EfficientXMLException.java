package eggum.ksoaptester;

class EfficientXMLException extends Exception
{
  public EfficientXMLException()
  {
  }

  public EfficientXMLException(String message)
  {
    super(message);
  }

  public EfficientXMLException(String message, Throwable cause) {
    super(message, cause);
  }

  public EfficientXMLException(Throwable cause) {
    super(cause);
  }
}