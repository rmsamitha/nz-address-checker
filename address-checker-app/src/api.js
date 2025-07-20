import axios from 'axios';

const NZ_POST_URL = process.env.REACT_APP_ADDRESS_CHECKER_API_BASE_URL + "suggestions";

export const fetchAddressSuggestions = async (query) => {
  console.log("Url:" + NZ_POST_URL);  
  console.log("Query passed:" + query);  
  if (!query) return [];

  try {
    const response = await axios.get(NZ_POST_URL, {
      params: {
        q: query,
        max: 6
      },
      headers: {
        'Accept': 'application/json'
      }
    });
    console.log("Raw response from API:", response); // <-- log here
    return response || [];
  } catch (error) {
    console.error('NZ Post API error:', error);
    return [];
  }
};
