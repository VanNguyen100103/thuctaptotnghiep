/**
 * Removes markdown formatting characters from AI-generated text
 * Cleans up #, *, **, and converts - to bullet points
 * Useful for cleaning AI-generated analysis text before display
 *
 * @param text - The text to clean
 * @returns The cleaned text without markdown characters
 */
export const cleanAIText = (text: string): string => {
  return text
    .replace(/#{1,6}\s*/g, '')      // Remove ### heading markers
    .replace(/\*\*/g, '')            // Remove ** bold markers
    .replace(/\*/g, '')              // Remove * italic markers
    .replace(/^-\s*/gm, '• ')        // Replace - with bullet points
    .trim();
};
